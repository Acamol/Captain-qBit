package dev.yashgarg.qbit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.databinding.ActivityMainBinding
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.worker.StatusWorker
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var clientManager: ClientManager
    @Inject lateinit var serverPrefsStore: DataStore<ServerPreferences>

    private val backupViewModel by viewModels<BackupViewModel>()

    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Starts disabled: the dispatcher invokes callbacks in reverse-registration order, so if
        // this were always enabled it would win over the NavHostFragment's own (earlier-
        // registered) back handling on every screen, not just the root one. Only enabled when
        // there's truly no previous destination to navigate back to.
        val exitOnBackPressed =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < EXIT_CONFIRMATION_WINDOW_MS) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(
                                this@MainActivity,
                                "Press back again to exit",
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
        onBackPressedDispatcher.addCallback(this, exitOnBackPressed)
        // NavHostFragment.navController is safe to read this early; Navigation.findNavController
        // (view-tag based) isn't - the tag isn't set until the child fragment's view is created.
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.addOnDestinationChangedListener { controller, _, _ ->
            exitOnBackPressed.isEnabled = controller.previousBackStackEntry == null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermissions(applicationContext)
        }

        // Apply theme/dynamic colors from the persisted prefs. Driving this off the stored value
        // (rather than a one-shot import event) is what makes a restored theme take effect: the
        // import can navigate away and tear down the fragment collector before an event is seen,
        // but this observer lives on the activity and survives that.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                serverPrefsStore.data
                    .map { it.themeMode to it.dynamicColors }
                    .distinctUntilChanged()
                    .collect { (themeMode, dynamicColors) ->
                        var recreated = false
                        if (themeMode != AppCompatDelegate.getDefaultNightMode()) {
                            // Recreates started activities to apply the new night mode.
                            AppCompatDelegate.setDefaultNightMode(themeMode)
                            recreated = true
                        }
                        if (QbitApplication.dynamicColorsEnabled != dynamicColors) {
                            QbitApplication.dynamicColorsEnabled = dynamicColors
                            if (!recreated) recreate()
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
                        // Theme/dynamic colors and leaving the empty state are handled by the prefs
                        // and config-status observers, so this only needs to acknowledge the
                        // import.
                        is BackupViewModel.BackupEvent.Imported ->
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT)
                                .show()
                        // Export isn't triggered from an opened file.
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
                            val bundle = bundleOf(TORRENT_INTENT_KEY to intent?.data.toString())
                            val navController =
                                findNavController(this@MainActivity, R.id.nav_host_fragment)

                            if (navController.currentDestination?.id == R.id.homeFragment) {
                                navController.navigate(
                                    R.id.action_homeFragment_to_serverFragment,
                                    bundle,
                                )
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
        // forward (if we're elsewhere, e.g. the info screen) so its add dialog can surface.
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) popToServerList()
    }

    private fun popToServerList() {
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                ?.navController ?: return
        if (navController.currentDestination?.id != R.id.serverFragment) {
            navController.popBackStack(R.id.serverFragment, false)
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

        // Prevent the config-status collector from treating this URI as a torrent to add.
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
