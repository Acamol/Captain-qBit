package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.backup.BackupDialogs
import dev.yashgarg.qbit.ui.backup.BackupViewModel
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.ui.rss.MaxArticlesPerFeedDialog
import dev.yashgarg.qbit.ui.rss.RefreshIntervalDialog
import dev.yashgarg.qbit.ui.server.SpeedLimitsDialog
import dev.yashgarg.qbit.worker.StatusWorker

private val BACKUP_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

private val THEME_OPTIONS =
    listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to CommonR.string.theme_system_default,
        AppCompatDelegate.MODE_NIGHT_NO to CommonR.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES to CommonR.string.theme_dark,
    )

@Composable
private fun themeLabel(mode: Int): String =
    stringResource(
        THEME_OPTIONS.firstOrNull { it.first == mode }?.second
            ?: CommonR.string.theme_system_default
    )

private val INTERVAL_OPTIONS =
    listOf(
        5_000L to CommonR.string.interval_5_seconds,
        10_000L to CommonR.string.interval_10_seconds,
        30_000L to CommonR.string.interval_30_seconds,
        60_000L to CommonR.string.interval_1_minute,
        300_000L to CommonR.string.interval_5_minutes,
    )

@Composable
private fun intervalLabel(ms: Long): String =
    stringResource(
        INTERVAL_OPTIONS.firstOrNull { it.first == ms }?.second ?: CommonR.string.interval_5_seconds
    )

// Per-app language: "" is the system-default locale list. Add a language's tag + label here (and
// to res/xml/locale_config.xml) as translations land.
private val LANGUAGE_OPTIONS =
    listOf(
        "" to CommonR.string.theme_system_default,
        "en" to CommonR.string.language_english,
        "he" to CommonR.string.language_hebrew,
    )

@Composable
private fun languageLabel(tag: String): String =
    stringResource(
        LANGUAGE_OPTIONS.firstOrNull { it.first == tag }?.second
            ?: CommonR.string.theme_system_default
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val backupPassphraseTitle = stringResource(CommonR.string.backup_passphrase_title)
    val encryptBackupTitle = stringResource(CommonR.string.encrypt_backup_title)

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColors by viewModel.dynamicColors.collectAsStateWithLifecycle()
    val autoTmmEnabled by viewModel.autoTmmEnabled.collectAsStateWithLifecycle()
    val rssRefreshIntervalMinutes by
        viewModel.rssRefreshIntervalMinutes.collectAsStateWithLifecycle()
    val rssMaxArticlesPerFeed by viewModel.rssMaxArticlesPerFeed.collectAsStateWithLifecycle()
    val rssProcessingEnabled by viewModel.rssProcessingEnabled.collectAsStateWithLifecycle()
    val rssAutoDownloadingEnabled by
        viewModel.rssAutoDownloadingEnabled.collectAsStateWithLifecycle()
    val queueingEnabled by viewModel.queueingEnabled.collectAsStateWithLifecycle()
    val speedLimitMode by viewModel.speedLimitMode.collectAsStateWithLifecycle()
    val globalDownloadLimit by viewModel.globalDownloadLimit.collectAsStateWithLifecycle()
    val globalUploadLimit by viewModel.globalUploadLimit.collectAsStateWithLifecycle()
    val altDownloadLimit by viewModel.altDownloadLimit.collectAsStateWithLifecycle()
    val altUploadLimit by viewModel.altUploadLimit.collectAsStateWithLifecycle()
    val statusNotif by viewModel.statusNotification.collectAsStateWithLifecycle()
    val notifyComplete by viewModel.notifyOnComplete.collectAsStateWithLifecycle()
    val notifyChecked by viewModel.notifyOnChecked.collectAsStateWithLifecycle()
    val notifyRssUpdates by viewModel.notifyOnNewRssArticles.collectAsStateWithLifecycle()
    val statusRefreshIntervalMs by viewModel.statusRefreshIntervalMs.collectAsStateWithLifecycle()
    val eventPollIntervalMs by viewModel.eventPollIntervalMs.collectAsStateWithLifecycle()
    val syncIntervalMs by viewModel.syncIntervalMs.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentLanguageTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    var showStatusIntervalDialog by remember { mutableStateOf(false) }
    var showEventIntervalDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showGlobalLimitsDialog by remember { mutableStateOf(false) }
    var showAltLimitsDialog by remember { mutableStateOf(false) }
    var showRssIntervalDialog by remember { mutableStateOf(false) }
    var showRssMaxArticlesDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }

    // Re-checked on resume so coming back from the system notification settings screen (via the
    // banner below) reflects the change immediately, not just on next screen open.
    var notificationsEnabled by remember {
        mutableStateOf(AppNotificationManager.notificationsEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = AppNotificationManager.notificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Only worth flagging if the user actually wants one of these to show something.
    val notificationsBlocked =
        !notificationsEnabled &&
            (statusNotif || notifyComplete || notifyChecked || notifyRssUpdates)

    val notifPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            notificationsEnabled = AppNotificationManager.notificationsEnabled(context)
            if (notificationsEnabled) StatusWorker.enqueue(context)
        }

    fun applyNotificationPrefs(status: Boolean, complete: Boolean, checked: Boolean, rss: Boolean) {
        if (!(status || complete || checked || rss)) {
            StatusWorker.cancel(context)
            return
        }
        when {
            AppNotificationManager.notificationsEnabled(context) -> StatusWorker.enqueue(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !AppNotificationManager.checkPermission(context) ->
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Runtime permission is already granted (or this is pre-33, which has none) but
            // notifications are still blocked at the app level - no system dialog can fix that,
            // only the notification settings screen can, same as the banner below.
            else -> openAppNotificationSettings(context)
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val pending = pendingExport
            pendingExport = null
            if (uri != null && pending != null) {
                backupViewModel.exportConfig(
                    uri,
                    pending.passphrase,
                    pending.serverIds,
                    pending.prefGroups,
                    pending.includeCategoryColors,
                )
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                BackupDialogs.showPassphraseDialog(
                    context,
                    title = backupPassphraseTitle,
                    confirm = false,
                ) { passphrase ->
                    backupViewModel.beginImport(uri, passphrase)
                }
            }
        }

    LaunchedEffect(Unit) {
        backupViewModel.backupEvents.collect { event ->
            when (event) {
                is BackupViewModel.BackupEvent.Exported ->
                    snackbarHostState.showSnackbar(event.message)
                is BackupViewModel.BackupEvent.Failed ->
                    snackbarHostState.showSnackbar(event.message)
                is BackupViewModel.BackupEvent.Loaded ->
                    BackupDialogs.showImportSelectionDialog(
                        context,
                        event.backup,
                        event.duplicateServerIds,
                    ) { serverIds, prefGroups, includeColors, mode ->
                        backupViewModel.applyImport(serverIds, prefGroups, includeColors, mode)
                    }
                is BackupViewModel.BackupEvent.Imported ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startExport() {
        BackupDialogs.showExportSelectionDialog(context, backupViewModel.servers.value) {
            serverIds,
            prefGroups,
            includeColors ->
            BackupDialogs.showPassphraseDialog(
                context,
                title = encryptBackupTitle,
                confirm = true,
            ) { passphrase ->
                pendingExport = PendingExport(passphrase, serverIds, prefGroups, includeColors)
                exportLauncher.launch("captain-qbit-backup.cqb")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(CommonR.string.settings_label)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // No bottomBar here - see NoWindowInsets kdoc.
        contentWindowInsets = NoWindowInsets,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(CommonR.string.general_section_title))
            ClickableRow(
                title = stringResource(CommonR.string.servers_title),
                onClick = { appNavigator.navigate(NavCommand.OpenServerList) },
            )

            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.server_section_title))
            Text(
                stringResource(CommonR.string.server_section_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            SwitchRow(
                stringResource(CommonR.string.auto_tmm_default_label),
                autoTmmEnabled,
                subtitle = stringResource(CommonR.string.auto_tmm_default_subtitle),
            ) {
                viewModel.setAutoTmmEnabled(it)
            }
            ClickableRow(
                title = stringResource(CommonR.string.rss_refresh_interval_label),
                subtitle = stringResource(CommonR.string.minutes_suffix, rssRefreshIntervalMinutes),
                onClick = { showRssIntervalDialog = true },
            )
            ClickableRow(
                title = stringResource(CommonR.string.rss_max_articles_per_feed_label),
                subtitle = stringResource(CommonR.string.articles_suffix, rssMaxArticlesPerFeed),
                onClick = { showRssMaxArticlesDialog = true },
            )
            SwitchRow(
                stringResource(CommonR.string.fetch_rss_feeds_label),
                rssProcessingEnabled,
                subtitle = stringResource(CommonR.string.fetch_rss_feeds_subtitle),
            ) {
                viewModel.setRssProcessingEnabled(it)
            }
            SwitchRow(
                stringResource(CommonR.string.auto_download_rss_label),
                rssAutoDownloadingEnabled,
                subtitle = stringResource(CommonR.string.auto_download_rss_subtitle),
            ) {
                viewModel.setRssAutoDownloadingEnabled(it)
            }
            SwitchRow(
                stringResource(CommonR.string.use_alternate_speed_limits_label),
                speedLimitMode != 0,
                subtitle = stringResource(CommonR.string.use_alternate_speed_limits_subtitle),
            ) {
                viewModel.toggleSpeedLimits()
            }
            ClickableRow(
                title = stringResource(CommonR.string.global_speed_limits_label),
                subtitle = stringResource(CommonR.string.global_speed_limits_subtitle),
                onClick = { showGlobalLimitsDialog = true },
            )
            ClickableRow(
                title = stringResource(CommonR.string.alternate_speed_limits_label),
                subtitle = stringResource(CommonR.string.alternate_speed_limits_subtitle),
                onClick = { showAltLimitsDialog = true },
            )
            SwitchRow(
                stringResource(CommonR.string.torrent_queueing_label),
                queueingEnabled,
                subtitle = stringResource(CommonR.string.torrent_queueing_subtitle),
            ) {
                viewModel.setQueueingEnabled(it)
            }

            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.appearance_section_title))
            ClickableRow(
                title = stringResource(CommonR.string.theme_label),
                subtitle = themeLabel(themeMode),
                onClick = { showThemeDialog = true },
            )
            ClickableRow(
                title = stringResource(CommonR.string.language_label),
                subtitle = languageLabel(currentLanguageTag),
                onClick = { showLanguageDialog = true },
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                SwitchRow(
                    stringResource(CommonR.string.dynamic_colors_label),
                    dynamicColors,
                    subtitle = stringResource(CommonR.string.dynamic_colors_subtitle),
                ) {
                    viewModel.setDynamicColors(it)
                }
            }

            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.notifications_section_title))
            if (notificationsBlocked) {
                NotificationsBlockedBanner(
                    onOpenSettings = { openAppNotificationSettings(context) }
                )
            }
            SwitchRow(
                stringResource(CommonR.string.status_notification_label),
                statusNotif,
                subtitle = stringResource(CommonR.string.status_notification_subtitle),
                enabled = !notificationsBlocked,
            ) {
                viewModel.setStatusNotification(it)
                applyNotificationPrefs(it, notifyComplete, notifyChecked, notifyRssUpdates)
            }
            SwitchRow(
                stringResource(CommonR.string.notify_on_complete_label),
                notifyComplete,
                subtitle = stringResource(CommonR.string.notify_on_complete_subtitle),
                enabled = !notificationsBlocked,
            ) {
                viewModel.setNotifyOnComplete(it)
                applyNotificationPrefs(statusNotif, it, notifyChecked, notifyRssUpdates)
            }
            SwitchRow(
                stringResource(CommonR.string.notify_on_checked_label),
                notifyChecked,
                subtitle = stringResource(CommonR.string.notify_on_checked_subtitle),
                enabled = !notificationsBlocked,
            ) {
                viewModel.setNotifyOnChecked(it)
                applyNotificationPrefs(statusNotif, notifyComplete, it, notifyRssUpdates)
            }
            SwitchRow(
                stringResource(CommonR.string.notify_on_new_rss_label),
                notifyRssUpdates,
                subtitle = stringResource(CommonR.string.notify_on_new_rss_subtitle),
                enabled = !notificationsBlocked,
            ) {
                viewModel.setNotifyOnNewRssArticles(it)
                applyNotificationPrefs(statusNotif, notifyComplete, notifyChecked, it)
            }
            ClickableRow(
                title = stringResource(CommonR.string.notification_refresh_interval_label),
                subtitle = intervalLabel(statusRefreshIntervalMs),
                onClick = { showStatusIntervalDialog = true },
            )
            ClickableRow(
                title = stringResource(CommonR.string.torrent_alert_check_interval_label),
                subtitle = intervalLabel(eventPollIntervalMs),
                onClick = { showEventIntervalDialog = true },
            )

            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.sync_section_title))
            ClickableRow(
                title = stringResource(CommonR.string.torrent_list_refresh_interval_label),
                subtitle = intervalLabel(syncIntervalMs),
                onClick = { showSyncIntervalDialog = true },
            )

            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.backup_section_title))
            ClickableRow(
                title = stringResource(CommonR.string.export_configuration_label),
                onClick = { startExport() },
            )
            ClickableRow(
                title = stringResource(CommonR.string.import_configuration_label),
                onClick = { importLauncher.launch(BACKUP_MIME_TYPES) },
            )

            HorizontalDivider()
            ClickableRow(
                title = stringResource(CommonR.string.about),
                onClick = { appNavigator.navigate(NavCommand.OpenVersion) },
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(CommonR.string.theme_label)) },
            text = {
                Column {
                    THEME_OPTIONS.forEach { (mode, labelRes) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(CommonR.string.language_label)) },
            text = {
                Column {
                    LANGUAGE_OPTIONS.forEach { (tag, labelRes) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        AppCompatDelegate.setApplicationLocales(
                                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                            else LocaleListCompat.forLanguageTags(tag)
                                        )
                                        currentLanguageTag = tag
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentLanguageTag == tag,
                                onClick = {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                        else LocaleListCompat.forLanguageTags(tag)
                                    )
                                    currentLanguageTag = tag
                                    showLanguageDialog = false
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    if (showStatusIntervalDialog) {
        IntervalDialog(
            title = stringResource(CommonR.string.notification_refresh_interval_label),
            description = stringResource(CommonR.string.notification_refresh_interval_description),
            selected = statusRefreshIntervalMs,
            onSelect = {
                viewModel.setStatusRefreshIntervalMs(it)
                // Restarts the already-running worker so it picks up the new interval right
                // away, instead of only after its current (possibly minutes-long) sleep ends.
                applyNotificationPrefs(statusNotif, notifyComplete, notifyChecked, notifyRssUpdates)
            },
            onDismiss = { showStatusIntervalDialog = false },
        )
    }
    if (showEventIntervalDialog) {
        IntervalDialog(
            title = stringResource(CommonR.string.torrent_alert_check_interval_label),
            description = stringResource(CommonR.string.torrent_alert_check_interval_description),
            selected = eventPollIntervalMs,
            onSelect = {
                viewModel.setEventPollIntervalMs(it)
                applyNotificationPrefs(statusNotif, notifyComplete, notifyChecked, notifyRssUpdates)
            },
            onDismiss = { showEventIntervalDialog = false },
        )
    }
    if (showSyncIntervalDialog) {
        IntervalDialog(
            title = stringResource(CommonR.string.torrent_list_refresh_interval_label),
            description = stringResource(CommonR.string.torrent_list_refresh_interval_description),
            selected = syncIntervalMs,
            onSelect = viewModel::setSyncIntervalMs,
            onDismiss = { showSyncIntervalDialog = false },
        )
    }
    if (showGlobalLimitsDialog) {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.global_speed_limits_label),
            initialDownloadBytes = globalDownloadLimit,
            initialUploadBytes = globalUploadLimit,
            onConfirm = { dl, ul -> viewModel.setGlobalLimits(dl, ul) },
            onDismiss = { showGlobalLimitsDialog = false },
        )
    }
    if (showAltLimitsDialog) {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.alternate_speed_limits_label),
            initialDownloadBytes = altDownloadLimit,
            initialUploadBytes = altUploadLimit,
            onConfirm = { dl, ul -> viewModel.setAltLimits(dl, ul) },
            onDismiss = { showAltLimitsDialog = false },
        )
    }
    if (showRssIntervalDialog) {
        RefreshIntervalDialog(
            currentMinutes = rssRefreshIntervalMinutes,
            onConfirm = {
                viewModel.setRssRefreshInterval(it)
                showRssIntervalDialog = false
            },
            onDismiss = { showRssIntervalDialog = false },
        )
    }
    if (showRssMaxArticlesDialog) {
        MaxArticlesPerFeedDialog(
            currentCount = rssMaxArticlesPerFeed,
            onConfirm = {
                viewModel.setRssMaxArticlesPerFeed(it)
                showRssMaxArticlesDialog = false
            },
            onDismiss = { showRssMaxArticlesDialog = false },
        )
    }
}

/** The selection + passphrase, held until the user picks an export destination. */
private data class PendingExport(
    val passphrase: String,
    val serverIds: Set<Int>,
    val prefGroups: Set<dev.yashgarg.qbit.data.backup.PrefGroup>,
    val includeCategoryColors: Boolean,
)

@Composable
private fun IntervalDialog(
    title: String,
    description: String,
    selected: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                INTERVAL_OPTIONS.forEach { (ms, label) ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onSelect(ms)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == ms,
                            onClick = {
                                onSelect(ms)
                                onDismiss()
                            },
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

@Composable
private fun NotificationsBlockedBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(CommonR.string.notifications_blocked_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(CommonR.string.notifications_blocked_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(CommonR.string.open_settings_action))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClickableRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .alpha(if (enabled) 1f else 0.38f)
                .clickable(enabled = enabled) { onChange(!checked) }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
