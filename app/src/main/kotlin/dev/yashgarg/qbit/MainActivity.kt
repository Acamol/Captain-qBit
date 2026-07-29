package dev.yashgarg.qbit

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.PendingTorrentIntent
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.crash.CrashReportDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.QbitNavHost
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewDialog
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewViewModel
import dev.yashgarg.qbit.utils.CrashHandler
import dev.yashgarg.qbit.utils.GitHubIssueLink
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.worker.StatusWorker
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var clientManager: ClientManager
    @Inject lateinit var serverPrefsStore: DataStore<ServerPreferences>
    @Inject lateinit var appNavigator: AppNavigator
    @Inject lateinit var pendingTorrentIntent: PendingTorrentIntent

    private val backupViewModel by viewModels<BackupViewModel>()
    private val whatsNewViewModel by viewModels<WhatsNewViewModel>()

    private var lastBackPressTime = 0L
    // Land on the torrent list once when a server exists; don't re-route on later config-status
    // replays (e.g. resume).
    private var navigatedToServer = false
    // A "download complete" notification tap arriving before a server exists (or before the
    // RESUMED collector below has run) — applied once OpenServerAsRoot has actually fired, so it
    // can't be wiped out by that command's own popUpTo.
    private var pendingNotificationHash: String? = null
    // Same idea, for a "new RSS articles" notification tap.
    private var pendingNotificationRssPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        setContent {
            val dynamicColorsFlow = remember { serverPrefsStore.data.map { it.dynamicColors } }
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = false)
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

                var crashReport by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    crashReport =
                        withContext(Dispatchers.IO) {
                            CrashHandler.consumePendingReport(applicationContext)
                        }
                }
                crashReport?.let { report ->
                    val copyToClipboard = rememberCopyToClipboard()
                    CrashReportDialog(
                        report = report,
                        onDismiss = { crashReport = null },
                        onCopy = {
                            copyToClipboard("Crash report", report, "Crash report copied")
                        },
                        onReportIssue = {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, GitHubIssueLink.url(report).toUri())
                            )
                        },
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var showNotificationRationale by remember { mutableStateOf(false) }
                    val notificationPermissionLauncher =
                        rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) launchWorkManager(true)
                        }
                    // Only ever asked once per install (tracked in prefs) - a denial doesn't nag
                    // again on every later launch. Skipped entirely when this launch is opening a
                    // backup file: that flow shows its own (native, non-Compose) passphrase dialog
                    // immediately in onCreate, which would otherwise land on screen at the same
                    // time as this one. Deferred to the next normal launch instead.
                    val openingBackupFile = intent?.data?.let(::isBackupUri) == true
                    LaunchedEffect(Unit) {
                        if (openingBackupFile) return@LaunchedEffect
                        val alreadyAsked =
                            serverPrefsStore.data.map { it.notificationPermissionAsked }.first()
                        if (
                            !alreadyAsked &&
                                !AppNotificationManager.checkPermission(this@MainActivity)
                        ) {
                            showNotificationRationale = true
                        }
                    }
                    if (showNotificationRationale) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Enable notifications?") },
                            text = {
                                Text(
                                    "Get a status notification with live transfer speeds, plus " +
                                        "alerts when a torrent finishes downloading or checking. " +
                                        "You can change this anytime in Settings."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showNotificationRationale = false
                                        lifecycleScope.launch {
                                            serverPrefsStore.updateData {
                                                it.copy(notificationPermissionAsked = true)
                                            }
                                        }
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                ) {
                                    Text("Enable")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showNotificationRationale = false
                                        lifecycleScope.launch {
                                            serverPrefsStore.updateData {
                                                it.copy(notificationPermissionAsked = true)
                                            }
                                        }
                                    }
                                ) {
                                    Text("Not now")
                                }
                            },
                        )
                    }
                }
            }
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
                // Re-run whenever a notification pref changes too, so enabling a toggle in Settings
                // actually (re)starts the status worker — not only on the Activity's first resume
                // with a server configured.
                combine(
                        clientManager.configStatus,
                        serverPrefsStore.data
                            .map {
                                it.statusNotification ||
                                    it.notifyOnComplete ||
                                    it.notifyOnChecked ||
                                    it.notifyOnNewRssArticles
                            }
                            .distinctUntilChanged(),
                    ) { status, notify ->
                        status to notify
                    }
                    // configStatus re-emits EXISTS on config edits / server switches; without this
                    // the worker gets REPLACE-re-enqueued each time, flickering the notification.
                    .distinctUntilChanged()
                    .collect { (status, notify) ->
                        when (status) {
                            ConfigStatus.EXISTS -> {
                                launchWorkManager(notify)
                                Log.i(
                                    ClientManager.tag,
                                    "Config exists (navigatedToServer=$navigatedToServer)",
                                )
                                if (!navigatedToServer) {
                                    navigatedToServer = true
                                    appNavigator.navigate(NavCommand.OpenServerAsRoot)
                                    // Must queue behind OpenServerAsRoot, not race it — that
                                    // command's popUpTo would otherwise wipe out a torrent/RSS
                                    // navigation sent first.
                                    pendingNotificationHash?.let { hash ->
                                        pendingNotificationHash = null
                                        appNavigator.navigate(NavCommand.OpenTorrent(hash))
                                    }
                                    pendingNotificationRssPath?.let { path ->
                                        pendingNotificationRssPath = null
                                        appNavigator.navigate(NavCommand.OpenRssArticles(path))
                                    }
                                }
                            }
                            ConfigStatus.DOES_NOT_EXIST ->
                                Log.i(ClientManager.tag, "No config found!")
                        }
                    }
            }
        }

        handleBackupIntent(intent)
        handleTorrentViewIntent(intent)
        pendingNotificationHash = intent.getStringExtra(EXTRA_TORRENT_HASH)
        pendingNotificationRssPath = intent.getStringExtra(EXTRA_RSS_ITEM_PATH)
    }

    // singleInstance: an already-running task receives opened files here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBackupIntent(intent)
        // handleBackupIntent clears the data for .cqb files, so remaining VIEW data means a
        // torrent. Hand its URI to ServerScreen (via PendingTorrentIntent) and bring the list
        // forward so its add dialog can surface.
        if (handleTorrentViewIntent(intent)) {
            appNavigator.navigate(NavCommand.PopToServer)
        }
        // Notification taps never reach here: notifyEvent()'s PendingIntent sets
        // FLAG_ACTIVITY_CLEAR_TASK, which always destroys and recreates this singleInstance
        // activity — so EXTRA_TORRENT_HASH/EXTRA_RSS_ITEM_PATH are only ever read in onCreate.
    }

    /** Offers a non-backup VIEW intent's URI to [pendingTorrentIntent]. Returns true if it did. */
    private fun handleTorrentViewIntent(intent: Intent): Boolean {
        val uri = intent.data
        if (intent.action != Intent.ACTION_VIEW || uri == null) return false
        pendingTorrentIntent.offer(uri.toString())
        return true
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

    private fun launchWorkManager(show: Boolean) {
        if (show && AppNotificationManager.notificationsEnabled(applicationContext)) {
            StatusWorker.enqueue(applicationContext)
        } else {
            StatusWorker.cancel(applicationContext)
        }
    }

    companion object {
        /** Extra key for the torrent hash carried by a "download complete" notification's tap. */
        const val EXTRA_TORRENT_HASH = "torrent_hash"
        /**
         * Extra key for the feed's item path carried by a "new RSS articles" notification's tap.
         */
        const val EXTRA_RSS_ITEM_PATH = "rss_item_path"
        private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L
    }
}
