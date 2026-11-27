package dev.yashgarg.qbit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.rss.MaxArticlesPerFeedDialog
import dev.yashgarg.qbit.ui.rss.RefreshIntervalDialog

/** Preferences stored on the connected qBittorrent server rather than in this app. */
@Composable
fun ServerOptionsContent(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel,
) {
    LaunchedEffect(Unit) { viewModel.loadServerOptions(refresh = true) }

    val autoTmmEnabled by viewModel.autoTmmEnabled.collectAsStateWithLifecycle()
    val rssRefreshIntervalMinutes by
        viewModel.rssRefreshIntervalMinutes.collectAsStateWithLifecycle()
    val rssMaxArticlesPerFeed by viewModel.rssMaxArticlesPerFeed.collectAsStateWithLifecycle()
    val rssProcessingEnabled by viewModel.rssProcessingEnabled.collectAsStateWithLifecycle()
    val rssAutoDownloadingEnabled by
        viewModel.rssAutoDownloadingEnabled.collectAsStateWithLifecycle()
    val queueingEnabled by viewModel.queueingEnabled.collectAsStateWithLifecycle()

    var showRssIntervalDialog by remember { mutableStateOf(false) }
    var showRssMaxArticlesDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
        ClickableRow(
            title = stringResource(CommonR.string.speed_limits_title),
            subtitle = stringResource(CommonR.string.speed_limits_subtitle),
            onClick = { appNavigator.navigate(NavCommand.OpenSpeedLimits) },
        )
        SwitchRow(
            stringResource(CommonR.string.torrent_queueing_label),
            queueingEnabled,
            subtitle = stringResource(CommonR.string.torrent_queueing_subtitle),
        ) {
            viewModel.setQueueingEnabled(it)
        }
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
