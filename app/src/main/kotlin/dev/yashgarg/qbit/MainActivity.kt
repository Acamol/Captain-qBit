package dev.yashgarg.qbit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.QbitNavHost
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewDialog
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewViewModel
import dev.yashgarg.qbit.worker.StatusWorker
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var clientManager: ClientManager
    @Inject lateinit var serverPrefsStore: DataStore<ServerPreferences>
    @Inject lateinit var appNavigator: AppNavigator

    private val backupViewModel by viewModels<BackupViewModel>()
    private val whatsNewViewModel by viewModels<WhatsNewViewModel>()

    private var lastBackPressTime = 0L
    // Land on the torrent list once when a server exists; don't re-route on later config-status
    // replays (e.g. resume).
    private var navigatedToServer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        setContent {
            val dynamicColors by
                serverPrefsStore.data
                    .map { it.dynamicColors }
                    .collectAsStateWithLifecycle(initialValue = false)
            QbitComposeTheme(dynamicColors = dynamicColors) {
                QbitNavHost(appNavigator = appNavigator, onExitDoubleBack = ::onExitDoubleBack)

                val whatsNew by whatsNewViewModel.uiState.collectAsStateWithLifecycle()
                if (whatsNew.visible) {
                    WhatsNewDialog(
                        versionName = whatsNew.versionName,
                        entries = whatsNew.entries,
                        onDismiss = whatsNewViewModel::dismiss,
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermissions(applicationContext)
        }

        // Apply the persisted theme mode (Light / Dark / Follow system). Driving this off the
        // stored value (rather than a one-shot import event) is what makes a restored theme take
        // effect: the import can navigate away and tear down a screen collector before an event is
        // seen, but this observer lives on the activity and survives that. Dynamic colors are read
        // reactively by the Compose theme (see setContent), so no activity recreate is needed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                serverPrefsStore.data
                    .map { it.themeMode }
                    .distinctUntilChanged()
                    .collect { themeMode ->
                        if (themeMode != AppCompatDelegate.getDefaultNightMode()) {
                            AppCompatDelegate.setDefaultNightMode(themeMode)
                        }
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                backupViewModel.backupEvents.collect { event ->
                    when (event) {
                        is BackupViewModel.BackupEvent.Failed ->
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG)
                                .show()
                        is BackupViewModel.BackupEvent.Loaded ->
                            BackupDialogs.showImportSelectionDialog(
                                this@MainActivity,
                                event.backup,
                                event.duplicateServerIds,
                            ) { serverIds, prefGroups, includeColors, mode ->
                                backupViewModel.applyImport(
                                    serverIds,
                                    prefGroups,
                                    includeColors,
                                    mode,
                                )
                            }
                        is BackupViewModel.BackupEvent.Imported ->
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT)
                                .show()
                        is BackupViewModel.BackupEvent.Exported -> Unit
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                clientManager.configStatus.collect { status ->
                    when (status) {
                        ConfigStatus.EXISTS -> {
                            val prefs = serverPrefsStore.data.first()
                            launchWorkManager(
                                prefs.statusNotification ||
                                    prefs.notifyOnComplete ||
                                    prefs.notifyOnChecked
                            )
                            if (!navigatedToServer) {
                                navigatedToServer = true
                                appNavigator.navigate(NavCommand.OpenServerAsRoot)
                            }
                        }
                        ConfigStatus.DOES_NOT_EXIST -> Log.i(ClientManager.tag, "No config found!")
                    }
                }
            }
        }

        handleBackupIntent(intent)
    }

    // singleInstance: an already-running task receives opened files here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBackupIntent(intent)
        // super.onNewIntent delivered a torrent to ServerFragment's listener; handleBackupIntent
        // clears the data for .cqb files, so remaining VIEW data means a torrent. Bring the list
        // forward so its add dialog can surface.
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            appNavigator.navigate(NavCommand.PopToServer)
        }
    }

    // "Press back twice to exit" at the navigation root (invoked by QbitNavHost's BackHandler).
    private fun onExitDoubleBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < EXIT_CONFIRMATION_WINDOW_MS) {
            finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * If this activity was opened on a .cqb backup file, start the import flow and consume the URI
     * so the torrent handling doesn't also try to add it. Import works whether or not any servers
     * exist, which is why it lives here rather than in a fragment.
     */
    private fun handleBackupIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || !isBackupUri(uri)) return

        // Prevent the torrent handling from treating this URI as a torrent to add.
        setIntent(intent.apply { data = null })

        BackupDialogs.showPassphraseDialog(this, title = "Backup passphrase", confirm = false) {
            passphrase ->
            backupViewModel.beginImport(uri, passphrase)
        }
    }

    /** True when the URI points at a Captain qBit backup, matched by its .cqb filename. */
    private fun isBackupUri(uri: Uri): Boolean {
        val name =
            if (uri.scheme == "content") {
                runCatching {
                        contentResolver
                            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                    }
                    .getOrNull()
            } else null
        val resolved = name ?: uri.lastPathSegment
        return resolved?.endsWith(".cqb", ignoreCase = true) == true
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions(context: Context) {
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                Log.i(
                    AppNotificationManager.javaClass.simpleName,
                    "Notification permission: $granted",
                )
                if (granted) launchWorkManager(true)
            }

        AppNotificationManager.requestPermission(context, permissionLauncher)
    }

    private fun launchWorkManager(show: Boolean) {
        if (show && AppNotificationManager.checkPermission(applicationContext)) {
            StatusWorker.enqueue(applicationContext)
        } else {
            StatusWorker.cancel(applicationContext)
        }
    }

    companion object {
        const val TORRENT_INTENT_KEY = "torrent_intent"
        private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L
    }
}
