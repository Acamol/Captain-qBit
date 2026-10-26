package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.navigation.NoWindowInsets
import dev.yashgarg.qbit.utils.isolateLtr
import java.util.Calendar

private val INTERVAL_OPTIONS =
    listOf(
        5_000L to CommonR.string.interval_5_seconds,
        10_000L to CommonR.string.interval_10_seconds,
        30_000L to CommonR.string.interval_30_seconds,
        60_000L to CommonR.string.interval_1_minute,
        300_000L to CommonR.string.interval_5_minutes,
    )

@Composable
internal fun intervalLabel(ms: Long): String =
    stringResource(
        INTERVAL_OPTIONS.firstOrNull { it.first == ms }?.second
            ?: CommonR.string.interval_5_seconds,
    )

// Formatted through the platform's own time format so it follows the device's 12/24-hour setting,
// and isolated because a time is LTR content that may sit in an RTL sentence.
internal fun timeOfDayLabel(context: Context, minutesOfDay: Int): String {
    val calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
        }
    return DateFormat.getTimeFormat(context).format(calendar.time).isolateLtr()
}

/** The top-level settings list: one row per settings page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val statusNotif by viewModel.statusNotification.collectAsStateWithLifecycle()
    val notifyComplete by viewModel.notifyOnComplete.collectAsStateWithLifecycle()
    val notifyChecked by viewModel.notifyOnChecked.collectAsStateWithLifecycle()
    val notifyRssUpdates by viewModel.notifyOnNewRssArticles.collectAsStateWithLifecycle()
    val privacyPrefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val appLockEnabled = privacyPrefs.appLockEnabled
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val activeServerName = activeServer?.name

    // Blocked notifications are flagged on the Notifications row too, so the problem stays
    // visible without opening that screen. Re-checked on resume, like the screen's own banner.
    var notificationsEnabled by remember {
        mutableStateOf(AppNotificationManager.notificationsEnabled(context))
    }
    fun localNetworkGranted() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                PackageManager.PERMISSION_GRANTED

    var localNetworkAllowed by remember { mutableStateOf(localNetworkGranted()) }
    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            localNetworkAllowed = granted
            // Once the permission is permanently denied the request returns denied without showing
            // anything, so asking again is a no-op and the banner would never clear. Only the app's
            // settings screen can still change it. Decided here rather than before the request,
            // because shouldShowRequestPermissionRationale is also false when never asked - it is
            // only conclusive after a denial.
            val activity = context as? Activity
            if (
                !granted &&
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_LOCAL_NETWORK,
                    )
            ) {
                openAppDetailsSettings(context)
            }
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = AppNotificationManager.notificationsEnabled(context)
        localNetworkAllowed = localNetworkGranted()
    }
    val notificationsBlocked =
        !notificationsEnabled &&
            (statusNotif || notifyComplete || notifyChecked || notifyRssUpdates)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(CommonR.string.settings_label)) })
        },
        // No bottomBar here - see NoWindowInsets kdoc.
        contentWindowInsets = NoWindowInsets,
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            // Android 17 gates LAN sockets behind a runtime permission, and a denial there is
            // fatal to reaching a self-hosted server. The one-time rationale on first launch is
            // not a recovery path, so offer one here for as long as it stays denied.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && !localNetworkAllowed) {
                LocalNetworkBlockedBanner(
                    onGrant = {
                        localNetworkPermissionLauncher.launch(
                            Manifest.permission.ACCESS_LOCAL_NETWORK,
                        )
                    },
                )
            }
            ClickableRow(
                title = stringResource(CommonR.string.servers_title),
                subtitle =
                    when {
                        activeServer == null -> null
                        activeServerName == null ->
                            stringResource(CommonR.string.servers_settings_subtitle_none)
                        else ->
                            stringResource(
                                CommonR.string.servers_settings_subtitle,
                                activeServerName,
                            )
                    },
                icon = Icons.Outlined.Dns,
                onClick = { appNavigator.navigate(NavCommand.OpenServerList) },
            )
            // Settings that live on the server itself, so there is nothing to show without one.
            activeServerName?.let { serverName ->
                ClickableRow(
                    title = stringResource(CommonR.string.server_settings_title),
                    subtitle = stringResource(CommonR.string.server_settings_subtitle, serverName),
                    icon = Icons.Outlined.Tune,
                    onClick = { appNavigator.navigate(NavCommand.OpenServerSettings) },
                )
            }
            ClickableRow(
                title = stringResource(CommonR.string.sync_section_title),
                subtitle = stringResource(CommonR.string.sync_settings_subtitle),
                icon = Icons.Outlined.Sync,
                onClick = { appNavigator.navigate(NavCommand.OpenSyncSettings) },
            )
            ClickableRow(
                title = stringResource(CommonR.string.notifications_section_title),
                subtitle =
                    stringResource(
                        if (notificationsBlocked) CommonR.string.notifications_blocked_title
                        else CommonR.string.notification_settings_subtitle,
                    ),
                subtitleIsError = notificationsBlocked,
                icon = Icons.Outlined.Notifications,
                onClick = { appNavigator.navigate(NavCommand.OpenNotificationSettings) },
            )
            ClickableRow(
                title = stringResource(CommonR.string.privacy_section_title),
                subtitle =
                    stringResource(
                        if (appLockEnabled) CommonR.string.privacy_settings_subtitle_lock_on
                        else CommonR.string.privacy_settings_subtitle_lock_off,
                    ),
                icon = Icons.Outlined.Lock,
                onClick = { appNavigator.navigate(NavCommand.OpenPrivacySettings) },
            )
            ClickableRow(
                title = stringResource(CommonR.string.appearance_section_title),
                subtitle = stringResource(CommonR.string.appearance_settings_subtitle),
                icon = Icons.Outlined.Palette,
                onClick = { appNavigator.navigate(NavCommand.OpenAppearanceSettings) },
            )
            ClickableRow(
                title = stringResource(CommonR.string.backup_section_title),
                subtitle = stringResource(CommonR.string.backup_settings_subtitle),
                icon = Icons.Outlined.SettingsBackupRestore,
                onClick = { appNavigator.navigate(NavCommand.OpenBackupSettings) },
            )
            ClickableRow(
                title = stringResource(CommonR.string.about),
                icon = Icons.Outlined.Info,
                onClick = { appNavigator.navigate(NavCommand.OpenVersion) },
            )
        }
    }
}

@Composable
internal fun IntervalDialog(
    title: String,
    description: String,
    selected: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<Long, Int>> = INTERVAL_OPTIONS,
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
                options.forEach { (ms, label) ->
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

/** The app's own settings page - the only place a permanently denied permission can be changed. */
private fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

@Composable
private fun LocalNetworkBlockedBanner(onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(CommonR.string.local_network_blocked_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(CommonR.string.local_network_blocked_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onGrant) { Text(stringResource(CommonR.string.grant_action)) }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ClickableRow(
    title: String,
    subtitle: String? = null,
    subtitleIsError: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (subtitleIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SwitchRow(
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

/** Time picker for one end of the quiet-hours window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayDialog(
    title: String,
    initialMinutesOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state =
        rememberTimePickerState(
            initialHour = initialMinutesOfDay / 60,
            initialMinute = initialMinutesOfDay % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(CommonR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** Scaffold for a settings page opened from the main settings screen: a back arrow and a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubScreen(
    title: String,
    appNavigator: AppNavigator,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(CommonR.string.content_description_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}
