package dev.yashgarg.qbit.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.worker.StatusWorker

private enum class SyncInterval {
    TORRENT_LIST,
    TORRENT_ALERTS,
}

/**
 * How often the app polls the server: in the foreground, and in the background for notifications.
 */
@Composable
fun SyncSettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val syncIntervalMs by viewModel.syncIntervalMs.collectAsStateWithLifecycle()
    val eventPollIntervalMs by viewModel.eventPollIntervalMs.collectAsStateWithLifecycle()
    val statusNotif by viewModel.statusNotification.collectAsStateWithLifecycle()
    val notifyComplete by viewModel.notifyOnComplete.collectAsStateWithLifecycle()
    val notifyChecked by viewModel.notifyOnChecked.collectAsStateWithLifecycle()
    val notifyRssUpdates by viewModel.notifyOnNewRssArticles.collectAsStateWithLifecycle()

    var editing by rememberSaveable { mutableStateOf<SyncInterval?>(null) }

    // Restarts the already-running worker so it picks up a new interval right away, instead of only
    // after its current (possibly minutes-long) sleep ends.
    fun restartWorker() {
        val anyEnabled = statusNotif || notifyComplete || notifyChecked || notifyRssUpdates
        if (anyEnabled && AppNotificationManager.notificationsEnabled(context)) {
            StatusWorker.enqueue(context)
        }
    }

    SettingsSubScreen(stringResource(CommonR.string.sync_section_title), appNavigator) {
        ClickableRow(
            title = stringResource(CommonR.string.torrent_list_refresh_interval_label),
            subtitle = intervalLabel(syncIntervalMs),
            onClick = { editing = SyncInterval.TORRENT_LIST },
        )
        ClickableRow(
            title = stringResource(CommonR.string.torrent_alert_check_interval_label),
            subtitle = intervalLabel(eventPollIntervalMs),
            onClick = { editing = SyncInterval.TORRENT_ALERTS },
        )
    }

    when (editing) {
        SyncInterval.TORRENT_LIST ->
            IntervalDialog(
                title = stringResource(CommonR.string.torrent_list_refresh_interval_label),
                description =
                    stringResource(CommonR.string.torrent_list_refresh_interval_description),
                selected = syncIntervalMs,
                onSelect = viewModel::setSyncIntervalMs,
                onDismiss = { editing = null },
            )
        SyncInterval.TORRENT_ALERTS ->
            IntervalDialog(
                title = stringResource(CommonR.string.torrent_alert_check_interval_label),
                description =
                    stringResource(CommonR.string.torrent_alert_check_interval_description),
                selected = eventPollIntervalMs,
                onSelect = {
                    viewModel.setEventPollIntervalMs(it)
                    restartWorker()
                },
                onDismiss = { editing = null },
            )
        null -> Unit
    }
}
