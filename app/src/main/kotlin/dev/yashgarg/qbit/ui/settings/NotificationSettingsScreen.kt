package dev.yashgarg.qbit.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.EventAlertMode
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.worker.StatusWorker

private val ALERT_MODE_OPTIONS =
    listOf(
        EventAlertMode.ALWAYS to CommonR.string.alert_sound_always,
        EventAlertMode.OUTSIDE_QUIET_HOURS to CommonR.string.alert_sound_outside_quiet_hours,
        EventAlertMode.NEVER to CommonR.string.alert_sound_never,
    )

@Composable
private fun alertModeLabel(mode: EventAlertMode): String =
    stringResource(
        ALERT_MODE_OPTIONS.firstOrNull { it.first == mode }?.second
            ?: CommonR.string.alert_sound_always,
    )

/** Which end of the quiet-hours window a time picker is editing. */
private enum class QuietEdge {
    START,
    END,
}

@Composable
fun NotificationSettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val statusNotif by viewModel.statusNotification.collectAsStateWithLifecycle()
    val notifyComplete by viewModel.notifyOnComplete.collectAsStateWithLifecycle()
    val notifyChecked by viewModel.notifyOnChecked.collectAsStateWithLifecycle()
    val notifyRssUpdates by viewModel.notifyOnNewRssArticles.collectAsStateWithLifecycle()
    val eventAlertMode by viewModel.eventAlertMode.collectAsStateWithLifecycle()
    val quietHoursStart by viewModel.quietHoursStartMinutes.collectAsStateWithLifecycle()
    val quietHoursEnd by viewModel.quietHoursEndMinutes.collectAsStateWithLifecycle()
    val statusRefreshIntervalMs by viewModel.statusRefreshIntervalMs.collectAsStateWithLifecycle()

    var showStatusIntervalDialog by remember { mutableStateOf(false) }

    // Which end of the quiet window the time picker is editing, null when it's closed.
    var editingQuietEdge by remember { mutableStateOf<QuietEdge?>(null) }
    var showAlertModeDialog by remember { mutableStateOf(false) }

    // Re-checked on resume so coming back from the system notification settings screen (via the
    // banner below) reflects the change immediately, not just on next screen open.
    var notificationsEnabled by remember {
        mutableStateOf(AppNotificationManager.notificationsEnabled(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = AppNotificationManager.notificationsEnabled(context)
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

    SettingsSubScreen(stringResource(CommonR.string.notifications_section_title), appNavigator) {
        if (notificationsBlocked) {
            NotificationsBlockedBanner(onOpenSettings = { openAppNotificationSettings(context) })
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
        if (statusNotif) {
            ClickableRow(
                title = stringResource(CommonR.string.notification_refresh_interval_label),
                subtitle = intervalLabel(statusRefreshIntervalMs),
                onClick = { showStatusIntervalDialog = true },
            )
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
        HorizontalDivider()
        ClickableRow(
            title = stringResource(CommonR.string.alert_sound_label),
            subtitle = alertModeLabel(eventAlertMode),
            onClick = { showAlertModeDialog = true },
        )
        if (eventAlertMode == EventAlertMode.OUTSIDE_QUIET_HOURS) {
            ClickableRow(
                title = stringResource(CommonR.string.quiet_hours_start_label),
                subtitle = timeOfDayLabel(context, quietHoursStart),
                onClick = { editingQuietEdge = QuietEdge.START },
            )
            ClickableRow(
                title = stringResource(CommonR.string.quiet_hours_end_label),
                subtitle = timeOfDayLabel(context, quietHoursEnd),
                onClick = { editingQuietEdge = QuietEdge.END },
            )
        }
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

    if (showAlertModeDialog) {
        AlertDialog(
            onDismissRequest = { showAlertModeDialog = false },
            title = { Text(stringResource(CommonR.string.alert_sound_label)) },
            text = {
                Column {
                    Text(
                        stringResource(CommonR.string.alert_sound_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    ALERT_MODE_OPTIONS.forEach { (mode, labelRes) ->
                        val choose: () -> Unit = {
                            viewModel.setEventAlertMode(mode)
                            showAlertModeDialog = false
                        }
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable(onClick = choose)
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = eventAlertMode == mode, onClick = choose)
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlertModeDialog = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    editingQuietEdge?.let { edge ->
        TimeOfDayDialog(
            title =
                stringResource(
                    if (edge == QuietEdge.START) CommonR.string.quiet_hours_start_label
                    else CommonR.string.quiet_hours_end_label,
                ),
            initialMinutesOfDay = if (edge == QuietEdge.START) quietHoursStart else quietHoursEnd,
            onConfirm = { minutes ->
                if (edge == QuietEdge.START) viewModel.setQuietHours(minutes, quietHoursEnd)
                else viewModel.setQuietHours(quietHoursStart, minutes)
                editingQuietEdge = null
            },
            onDismiss = { editingQuietEdge = null },
        )
    }
}

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
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
