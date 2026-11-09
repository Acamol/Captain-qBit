package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.TrackerStatus
import dev.yashgarg.qbit.ui.compose.AlertDialog
import dev.yashgarg.qbit.ui.compose.TextInputDialog
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TrackerSummary
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

    val green = colorResource(R.color.green)
    val yellow = colorResource(R.color.yellow)
    val red = colorResource(R.color.red)

    var dialog by remember { mutableStateOf<TrackerDialog?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        if (isPrivate) {
            item {
                Text(
                    stringResource(CommonR.string.private_torrent_trackers_fixed_message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        } else {
            item {
                TextButton(
                    onClick = { dialog = TrackerDialog.Add },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        stringResource(CommonR.string.add_tracker_action),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
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

        if (trackers.isNotEmpty()) {
            item { TrackerSummaryLine(trackers.summary()) }
        }

        // No stable key: a torrent can report the same tracker URL more than once (duplicated
        // across tiers), and a duplicate key crashes LazyColumn. Positional is fine for this list.
        itemsIndexed(trackers) { _, tracker ->
            TrackerCard(
                tracker = tracker,
                statusColor =
                    when (TrackerStatus.statusOf(tracker.status)) {
                        TrackerStatus.CONTACTED_WORKING -> green
                        TrackerStatus.UPDATING -> yellow
                        TrackerStatus.NOT_CONTACTED,
                        TrackerStatus.DISABLED -> MaterialTheme.colorScheme.outline
                        TrackerStatus.CONTACTED_NOT_WORKING -> red
                    },
                editable = !isPrivate && !tracker.isPseudoSource(),
                onEdit = { dialog = TrackerDialog.Edit(tracker.url) },
                onRemove = { dialog = TrackerDialog.Remove(tracker.url) },
                onLongPress = { dialog = TrackerDialog.Menu(tracker) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    when (val current = dialog) {
        is TrackerDialog.Menu ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(current.tracker.displayName()) },
                text = {
                    Column {
                        MenuRow(stringResource(CommonR.string.edit_tracker_label)) {
                            dialog = TrackerDialog.Edit(current.tracker.url)
                        }
                        MenuRow(stringResource(CommonR.string.remove_tracker_label)) {
                            dialog = TrackerDialog.Remove(current.tracker.url)
                        }
                    }
                },
                confirmButton = {},
            )
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
private fun TrackerSummaryLine(summary: TrackerSummary) {
    Text(
        listOfNotNull(
                pluralStringResource(
                    CommonR.plurals.tracker_summary_total,
                    summary.total,
                    summary.total,
                ),
                stringResource(CommonR.string.tracker_summary_working, summary.working),
                summary.notWorking
                    .takeIf { it > 0 }
                    ?.let { stringResource(CommonR.string.tracker_summary_not_working, it) },
            )
            .joinToString(" • "),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun TrackerCard(
    tracker: TorrentTracker,
    statusColor: Color,
    editable: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val status = TrackerStatus.statusOf(tracker.status)
    val unknown = stringResource(CommonR.string.search_unknown_value)
    fun Int.shown() = reportedOrNull()?.toString() ?: unknown

    ElevatedCard(
        modifier.fillMaxWidth().pointerInput(editable) {
            if (editable) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                )
            }
        },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tracker.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!tracker.isPseudoSource()) {
                        Text(
                            tracker.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (editable) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(CommonR.string.edit_tracker_label),
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription =
                                stringResource(CommonR.string.remove_tracker_label),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                Text(
                    stringResource(status.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (tracker.tier >= 0) {
                    Text(
                        " • " + stringResource(CommonR.string.tracker_tier, tracker.tier),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp)) {
                TrackerStat(tracker.numSeeds.shown(), stringResource(CommonR.string.seeds))
                TrackerStat(
                    tracker.numLeeches.shown(),
                    stringResource(CommonR.string.tracker_stat_leeches),
                )
                TrackerStat(tracker.numPeers.shown(), stringResource(CommonR.string.peers))
                TrackerStat(
                    tracker.numDownloaded.shown(),
                    stringResource(CommonR.string.downloaded),
                )
            }
            if (tracker.msg.isNotBlank()) {
                Text(
                    tracker.msg,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (status == TrackerStatus.CONTACTED_NOT_WORKING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.TrackerStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
