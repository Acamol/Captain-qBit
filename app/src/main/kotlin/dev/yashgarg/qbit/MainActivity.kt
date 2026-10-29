package dev.yashgarg.qbit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.PendingTorrentIntent
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.common.LockScreen
import dev.yashgarg.qbit.ui.crash.CrashReportDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.QbitNavHost
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewDialog
import dev.yashgarg.qbit.ui.whatsnew.WhatsNewViewModel
import dev.yashgarg.qbit.utils.CrashHandler
import dev.yashgarg.qbit.utils.GitHubIssueLink
import dev.yashgarg.qbit.utils.LocalizedContext
import dev.yashgarg.qbit.utils.WindowPrivacy
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.worker.StatusWorker
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var clientManager: ClientManager
    @Inject lateinit var appPrefsStore: DataStore<AppPreferences>
    @Inject lateinit var appNavigator: AppNavigator
    @Inject lateinit var pendingTorrentIntent: PendingTorrentIntent
    @Inject lateinit var appLockManager: AppLockManager

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
    private var pendingView: Intent? = null
    private var pendingViewBroughtForward = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        val launchIntent = intent

        // A non-null savedInstanceState means the nav host restores its own back stack, so the
        // initial routing below has already happened for this session. Re-running it would re-root
        // navigation and throw away wherever the user actually was - on every rotation.
        navigatedToServer = savedInstanceState != null
        if (savedInstanceState == null) appLockManager.onAppOpened()
        appLockManager.attach(this)

        // Applied before the first frame, not only as the collector below sees changes, which
        // would leave the window capturable until then.
        applyPrivacyFlags(appLockManager.windowPrivacy.value)

        setContent {
            val dynamicColorsFlow = remember { appPrefsStore.data.map { it.dynamicColors } }
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = false)
            val locked by appLockManager.locked.collectAsStateWithLifecycle()
            QbitComposeTheme(dynamicColors = dynamicColors) {
                // Kept composed while locked so the user returns to exactly where they were, but
                // neither drawn nor exposed to accessibility services.
                Box(
                    modifier = if (locked) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                ) {
                    QbitNavHost(
                        appNavigator = appNavigator,
                        onExitDoubleBack = ::onExitDoubleBack,
                    )
                }

                // Covers the app, dialogs included, until LockActivity is on top of it. The
                // app-level dialogs below wait for the unlock, so they don't open behind the lock.
                if (locked) {
                    LockScreen(
                        onUnlock = { LockActivity.start(this@MainActivity) },
                        onBack = { moveTaskToBack(true) },
                    )
                }

                val whatsNew by whatsNewViewModel.uiState.collectAsStateWithLifecycle()
                if (whatsNew.visible && !locked) {
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
                crashReport
                    ?.takeIf { !locked }
                    ?.let { report ->
                        val copyToClipboard = rememberCopyToClipboard()
                        val crashReportCopiedMessage =
                            stringResource(CommonR.string.crash_report_copied)
                        CrashReportDialog(
                            report = report,
                            onDismiss = { crashReport = null },
                            onCopy = {
                                copyToClipboard("Crash report", report, crashReportCopiedMessage)
                            },
                            onReportIssue = {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, GitHubIssueLink.url(report).toUri()),
                                )
                            },
                        )
                    }

                // Shared by both permission rationales below: neither is dismissable, so only
                // one may be on screen at a time, and both defer when this launch is opening a
                // backup file (that flow shows its own native passphrase dialog from onCreate).
                var showNotificationRationale by remember { mutableStateOf(false) }
                // Queried once: it asks the file's provider, and this is recomposed on every lock
                // change.
                val openingBackupFile = remember {
                    launchIntent.data?.let(::isBackupUri) == true
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermissionLauncher =
                        rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            if (granted) launchWorkManager(true)
                        }
                    // Only ever asked once per install (tracked in prefs) - a denial doesn't nag
                    // again on every later launch.
                    LaunchedEffect(Unit) {
                        if (openingBackupFile) return@LaunchedEffect
                        val alreadyAsked =
                            appPrefsStore.data.map { it.notificationPermissionAsked }.first()
                        if (
                            !alreadyAsked &&
                                !AppNotificationManager.checkPermission(this@MainActivity)
                        ) {
                            showNotificationRationale = true
                        }
                    }
                    if (showNotificationRationale && !locked) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = {
                                Text(stringResource(CommonR.string.enable_notifications_title))
                            },
                            text = {
                                Text(stringResource(CommonR.string.enable_notifications_message))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showNotificationRationale = false
                                        lifecycleScope.launch {
                                            appPrefsStore.updateData {
                                                it.copy(notificationPermissionAsked = true)
                                            }
                                        }
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    },
                                ) {
                                    Text(stringResource(CommonR.string.enable_action))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showNotificationRationale = false
                                        lifecycleScope.launch {
                                            appPrefsStore.updateData {
                                                it.copy(notificationPermissionAsked = true)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(CommonR.string.not_now_action))
                                }
                            },
                        )
                    }
                }

                // Android 17 requires a runtime permission for any socket to a local-network
                // address. Asked once per install, same as notifications above, and only when no
                // notification rationale is already on screen - two undismissable dialogs must
                // not stack.
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                        !showNotificationRationale
                ) {
                    var showLocalNetworkRationale by remember { mutableStateOf(false) }
                    val localNetworkPermissionLauncher =
                        rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) {}
                    LaunchedEffect(Unit) {
                        if (openingBackupFile) return@LaunchedEffect
                        val alreadyAsked =
                            appPrefsStore.data.map { it.localNetworkPermissionAsked }.first()
                        if (
                            !alreadyAsked &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                                ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            showLocalNetworkRationale = true
                        }
                    }
                    if (showLocalNetworkRationale && !locked) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = {
                                Text(stringResource(CommonR.string.enable_local_network_title))
                            },
                            text = {
                                Text(stringResource(CommonR.string.enable_local_network_message))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showLocalNetworkRationale = false
                                        lifecycleScope.launch {
                                            appPrefsStore.updateData {
                                                it.copy(localNetworkPermissionAsked = true)
                                            }
                                        }
                                        localNetworkPermissionLauncher.launch(
                                            Manifest.permission.ACCESS_LOCAL_NETWORK,
                                        )
                                    },
                                ) {
                                    Text(stringResource(CommonR.string.enable_action))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showLocalNetworkRationale = false
                                        lifecycleScope.launch {
                                            appPrefsStore.updateData {
                                                it.copy(localNetworkPermissionAsked = true)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(CommonR.string.not_now_action))
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
                appPrefsStore.data
                    .map { it.themeMode }
                    .distinctUntilChanged()
                    .collect { themeMode ->
                        if (themeMode != AppCompatDelegate.getDefaultNightMode()) {
                            AppCompatDelegate.setDefaultNightMode(themeMode)
                        }
                    }
            }
        }

        // Applies a language arriving from a restored backup, for the same reason the theme
        // observer
        // above exists: the import can tear down a screen collector before an event is seen.
        //
        // drop(1) discards DataStore's replay of the current value, so this only ever reacts to the
        // preference actually changing. That matters on API 33+, where the system's own per-app
        // language screen can change the locale without going through this app: enforcing the
        // stored value on startup would undo that choice on every launch. The Settings picker
        // applies its own selection directly and doesn't rely on this.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appPrefsStore.data
                    .map { it.languageTag }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { languageTag ->
                        if (
                            languageTag !=
                                AppCompatDelegate.getApplicationLocales().toLanguageTags()
                        ) {
                            AppCompatDelegate.setApplicationLocales(
                                if (languageTag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(languageTag),
                            )
                            // Android caches channel names from creation time, so a new locale
                            // alone won't retranslate them. Recreating with the same ids does.
                            AppNotificationManager.createNotificationChannel(
                                LocalizedContext.of(applicationContext),
                            )
                        }
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { BackupDialogs.offerImports(this@MainActivity, backupViewModel) }
                backupViewModel.backupEvents.collect { event ->
                    when (event) {
                        is BackupViewModel.BackupEvent.Failed ->
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG)
                                .show()
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
                        appPrefsStore.data
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLockManager.windowPrivacy.collect(::applyPrivacyFlags)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (appLockManager.disableIfUnavailable()) {
                    Toast.makeText(
                            this@MainActivity,
                            getString(CommonR.string.app_lock_turned_off_no_screen_lock),
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }
        }

        // A lock engaging while the app is on screen. Being shown while locked is covered in
        // onStart, which comes before this collector sees the current value.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appLockManager.locked.drop(1).collect { locked ->
                    if (locked) LockActivity.start(this@MainActivity)
                }
            }
        }

        // A recreated activity gets the launch intent again, so only a fresh launch opens it - a
        // file still waiting for the unlock comes back from the saved state instead.
        if (savedInstanceState == null) {
            openWhenUnlocked(launchIntent, broughtForward = false)
        } else {
            BundleCompat.getParcelable(savedInstanceState, STATE_PENDING_VIEW, Intent::class.java)
                ?.let {
                    openWhenUnlocked(
                        it,
                        savedInstanceState.getBoolean(STATE_PENDING_VIEW_BROUGHT_FORWARD),
                    )
                }
        }
        pendingNotificationHash = launchIntent.getStringExtra(EXTRA_TORRENT_HASH)
        pendingNotificationRssPath = launchIntent.getStringExtra(EXTRA_RSS_ITEM_PATH)
    }

    override fun onStart() {
        super.onStart()
        // Covered by the lock screen whenever it's shown while locked: on the app's return, a fresh
        // start, or an opened file or link bringing it forward. Started here, as early as the app
        // can, to keep the moment before it covers the app short.
        if (appLockManager.locked.value) LockActivity.start(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        appLockManager.detach(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_PENDING_VIEW, pendingView)
        outState.putBoolean(STATE_PENDING_VIEW_BROUGHT_FORWARD, pendingViewBroughtForward)
    }

    /**
     * Runs [block] once the app is resumed and unlocked. Waiting for the resume matters: an intent
     * delivered to a backgrounded app arrives before the timed lock has had its say on the return.
     */
    private fun whenUnlocked(block: () -> Unit) {
        lifecycleScope.launch {
            lifecycle.withResumed {}
            appLockManager.locked.first { !it }
            block()
        }
    }

    /**
     * Opens a file or link once the app is unlocked: both can put a dialog on screen (the add
     * torrent sheet, the backup passphrase), which would otherwise show above the lock. It is kept
     * in the saved state meanwhile, so recreating the activity before the unlock doesn't drop it. A
     * torrent [broughtForward] to an app already running also brings the list forward, so its add
     * dialog can surface.
     */
    private fun openWhenUnlocked(intent: Intent, broughtForward: Boolean) {
        if (intent.action != Intent.ACTION_VIEW || intent.data == null) return
        pendingView = intent
        pendingViewBroughtForward = broughtForward
        whenUnlocked {
            if (pendingView === intent) pendingView = null
            // Anything that isn't a backup is a torrent, handed to ServerScreen via
            // PendingTorrentIntent.
            if (!handleBackupIntent(intent) && handleTorrentViewIntent(intent) && broughtForward) {
                appNavigator.navigate(NavCommand.PopToServer)
            }
        }
    }

    private fun applyPrivacyFlags(privacy: WindowPrivacy) {
        if (privacy.secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(privacy.recentsScreenshotEnabled)
        }
    }

    // singleInstance: an already-running task receives opened files here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openWhenUnlocked(intent, broughtForward = true)
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
            Toast.makeText(
                    this,
                    getString(CommonR.string.press_back_again_to_exit),
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    /**
     * If this activity was opened on a .cqb backup file, start the import flow and consume the URI
     * so the torrent handling doesn't also try to add it. Import works whether or not any servers
     * exist, which is why it lives here rather than in a fragment.
     */
    private fun handleBackupIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (intent.action != Intent.ACTION_VIEW || !isBackupUri(uri)) return false

        // Consume the URI so a recreated activity won't show another import dialog. This may be a
        // saved launch intent while a newer intent is current, so don't overwrite the newer one.
        if (this.intent === intent) setIntent(Intent(intent).apply { data = null })

        BackupDialogs.showPassphraseDialog(
            this,
            title = getString(CommonR.string.backup_passphrase_title),
            confirm = false,
        ) { passphrase ->
            backupViewModel.beginImport(uri, passphrase)
        }
        return true
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
        private const val STATE_PENDING_VIEW = "pending_view"
        private const val STATE_PENDING_VIEW_BROUGHT_FORWARD = "pending_view_brought_forward"
    }
}
