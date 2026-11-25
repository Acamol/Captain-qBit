package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.TrackerStatus
import dev.yashgarg.qbit.ui.compose.ActionSheet
import dev.yashgarg.qbit.ui.compose.ActionSheetItem
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.TextInputDialog
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TrackerSummary
import dev.yashgarg.qbit.ui.torrent.additionalMessage
import dev.yashgarg.qbit.ui.torrent.displayName
import dev.yashgarg.qbit.ui.torrent.isPseudoSource
import dev.yashgarg.qbit.ui.torrent.reportedOrNull
import dev.yashgarg.qbit.ui.torrent.summary
import qbittorrent.models.TorrentTracker

@Composable
fun TrackersTab(
    state: TorrentDetailsState,
    onAddTrackers: (List<String>) -> Unit,
    onEditTracker: (originalUrl: String, newUrl: String) -> Unit,
    onRemoveTracker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackers = state.trackers
    // Private torrents have a fixed tracker list baked into their metadata; qBittorrent rejects any
    // attempt to add/edit/remove trackers on them. Null means the server didn't report it (older
    // versions) — leave editing enabled in that case rather than guessing.
    val isPrivate = state.torrentProperties?.isPrivate == true

    var dialog by remember { mutableStateOf<TrackerDialog?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackerSummaryLine(trackers.summary(), Modifier.weight(1f))
                if (!isPrivate) {
                    FilledTonalIconButton(onClick = { dialog = TrackerDialog.Add }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(CommonR.string.add_tracker_action),
                        )
                    }
                }
            }
        }
        if (isPrivate) {
            item {
                Text(
                    stringResource(CommonR.string.private_torrent_trackers_fixed_message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }

        if (trackers.isEmpty()) {
            item {
                Text(
                    stringResource(CommonR.string.no_trackers),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }

        // No stable key: a torrent can report the same tracker URL more than once (duplicated
        // across tiers), and a duplicate key crashes LazyColumn. Positional is fine for this list.
        itemsIndexed(trackers) { index, tracker ->
            TrackerCard(
                tracker = tracker,
                index = index,
                count = trackers.size,
                onLongPress = { dialog = TrackerDialog.Menu(tracker) },
            )
        }
    }

    when (val current = dialog) {
        is TrackerDialog.Menu ->
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = current.tracker.displayName(),
            ) {
                val tracker = current.tracker
                val statusLabel = stringResource(TrackerStatus.statusOf(tracker.status).labelRes())
                val message = tracker.additionalMessage(statusLabel)
                val unknown = stringResource(CommonR.string.search_unknown_value)
                Column(Modifier.padding(horizontal = 24.dp)) {
                    if (!tracker.isPseudoSource())
                        DetailRow(stringResource(CommonR.string.tracker_address_label), tracker.url)
                    DetailRow(
                        stringResource(CommonR.string.status_section_title),
                        statusLabel,
                    )
                    if (tracker.tier >= 0)
                        Text(
                            stringResource(CommonR.string.tracker_tier, tracker.tier),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    listOf(
                            CommonR.string.seeds to tracker.numSeeds,
                            CommonR.string.tracker_stat_leeches to tracker.numLeeches,
                            CommonR.string.peers to tracker.numPeers,
                            CommonR.string.downloaded to tracker.numDownloaded,
                        )
                        .forEach { (label, value) ->
                            DetailRow(
                                stringResource(label),
                                value.reportedOrNull()?.toString() ?: unknown,
                            )
                        }
                    if (message != null)
                        Text(
                            message,
                            color =
                                if (
                                    TrackerStatus.statusOf(tracker.status) ==
                                        TrackerStatus.CONTACTED_NOT_WORKING
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                if (!isPrivate && !tracker.isPseudoSource()) {
                    ActionSheetItem(
                        stringResource(CommonR.string.edit_tracker_label),
                        Icons.Filled.Edit,
                    ) {
                        dialog = TrackerDialog.Edit(current.tracker.url)
                    }
                    ActionSheetItem(
                        stringResource(CommonR.string.remove_tracker_label),
                        Icons.Filled.Delete,
                    ) {
                        dialog = TrackerDialog.Remove(current.tracker.url)
                    }
                }
            }
        TrackerDialog.Add ->
            TextInputDialog(
                title = stringResource(CommonR.string.add_trackers_title),
                confirmLabel = stringResource(CommonR.string.ok),
                validate = { null },
                singleLine = false,
                initial = "",
                // One tracker URL per line, matching the qBittorrent desktop dialog.
                supportingText = stringResource(CommonR.string.one_url_per_line),
                onConfirm = { text ->
                    val urls = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (urls.isNotEmpty()) onAddTrackers(urls)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is TrackerDialog.Edit ->
            TextInputDialog(
                title = stringResource(CommonR.string.edit_tracker_label),
                confirmLabel = stringResource(CommonR.string.ok),
                validate = { null },
                singleLine = false,
                initial = current.url,
                onConfirm = { newUrl ->
                    val trimmed = newUrl.trim()
                    if (trimmed.isNotEmpty() && trimmed != current.url) {
                        onEditTracker(current.url, trimmed)
                    }
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        is TrackerDialog.Remove ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(CommonR.string.remove_tracker_confirm_title)) },
                text = { Text(current.url) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemoveTracker(current.url)
                            dialog = null
                        },
                    ) {
                        Text(stringResource(CommonR.string.remove_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(CommonR.string.cancel))
                    }
                },
            )
        null -> Unit
    }
}

private sealed interface TrackerDialog {
    data object Add : TrackerDialog

    data class Menu(val tracker: TorrentTracker) : TrackerDialog

    data class Edit(val url: String) : TrackerDialog

    data class Remove(val url: String) : TrackerDialog
}

@Composable
private fun TrackerSummaryLine(summary: TrackerSummary, modifier: Modifier = Modifier) {
    Text(
        listOfNotNull(
                pluralStringResource(
                    CommonR.plurals.tracker_summary_total,
                    summary.total,
                    summary.total,
                ),
                pluralStringResource(
                    CommonR.plurals.tracker_summary_working,
                    summary.working,
                    summary.working,
                ),
                summary.notWorking
                    .takeIf { it > 0 }
                    ?.let {
                        pluralStringResource(CommonR.plurals.tracker_summary_not_working, it, it)
                    },
            )
            .joinToString(" • "),
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TrackerCard(
    tracker: TorrentTracker,
    index: Int,
    count: Int,
    onLongPress: () -> Unit,
) {
    val status = TrackerStatus.statusOf(tracker.status)
    val statusLabel = stringResource(status.labelRes())
    val message = tracker.additionalMessage(statusLabel)
    val statusColor =
        when (status) {
            TrackerStatus.CONTACTED_WORKING -> MaterialTheme.colorScheme.primary
            TrackerStatus.UPDATING -> MaterialTheme.colorScheme.tertiary
            TrackerStatus.CONTACTED_NOT_WORKING -> MaterialTheme.colorScheme.error
            TrackerStatus.NOT_CONTACTED,
            TrackerStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val statusIcon =
        when (status) {
            TrackerStatus.CONTACTED_WORKING -> Icons.Filled.CheckCircle
            TrackerStatus.UPDATING -> Icons.Filled.Sync
            TrackerStatus.CONTACTED_NOT_WORKING -> Icons.Filled.ErrorOutline
            TrackerStatus.NOT_CONTACTED,
            TrackerStatus.DISABLED -> Icons.Filled.RadioButtonUnchecked
        }
    val unknown = stringResource(CommonR.string.search_unknown_value)
    fun Int.shown() = reportedOrNull()?.toString() ?: unknown

    SegmentedListItem(
        onClick = onLongPress,
        onLongClick = onLongPress,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Icon(statusIcon, contentDescription = null, tint = statusColor) },
        trailingContent = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!tracker.isPseudoSource())
                    Text(
                        tracker.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        listOfNotNull(
                                statusLabel,
                                if (tracker.tier >= 0)
                                    stringResource(CommonR.string.tracker_tier, tracker.tier)
                                else null,
                            )
                            .joinToString(" · "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "${stringResource(CommonR.string.seeds)} ${tracker.numSeeds.shown()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "${stringResource(CommonR.string.peers)} ${tracker.numPeers.shown()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (status == TrackerStatus.CONTACTED_NOT_WORKING) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        },
    ) {
        Text(
            tracker.displayName(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private fun TrackerStatus.labelRes(): Int =
    when (this) {
        TrackerStatus.CONTACTED_WORKING -> CommonR.string.tracker_status_working
        TrackerStatus.UPDATING -> CommonR.string.tracker_status_updating
        TrackerStatus.NOT_CONTACTED -> CommonR.string.tracker_status_not_contacted
        TrackerStatus.CONTACTED_NOT_WORKING -> CommonR.string.tracker_status_not_working
        TrackerStatus.DISABLED -> CommonR.string.tracker_status_disabled
    }
