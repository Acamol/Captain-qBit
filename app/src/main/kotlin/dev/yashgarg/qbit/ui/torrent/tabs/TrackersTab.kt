package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.TrackerStatus
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState

// qBittorrent lists the DHT/PeX/LSD pseudo-sources as trackers whose URL is bracketed like
// "** [DHT] **"; those aren't real URLs and can't be edited or removed.
private fun String.isRealTracker() = !startsWith("**")

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

        // No stable key: a torrent can report the same tracker URL more than once (duplicated
        // across tiers), and a duplicate key crashes LazyColumn. Positional is fine for this list.
        itemsIndexed(trackers) { _, tracker ->
            val color: Color =
                when (TrackerStatus.statusOf(tracker.status)) {
                    TrackerStatus.CONTACTED_WORKING -> green
                    TrackerStatus.UPDATING -> yellow
                    else -> red
                }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tracker.url,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                )
                if (!isPrivate && tracker.url.isRealTracker()) {
                    IconButton(onClick = { dialog = TrackerDialog.Edit(tracker.url) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(CommonR.string.edit_tracker_label),
                        )
                    }
                    IconButton(onClick = { dialog = TrackerDialog.Remove(tracker.url) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription =
                                stringResource(CommonR.string.remove_tracker_label),
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        TrackerDialog.Add ->
            TrackerInputDialog(
                title = stringResource(CommonR.string.add_trackers_title),
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
            TrackerInputDialog(
                title = stringResource(CommonR.string.edit_tracker_label),
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
                        }
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

    data class Edit(val url: String) : TrackerDialog

    data class Remove(val url: String) : TrackerDialog
}

@Composable
private fun TrackerInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supportingText: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                supportingText = supportingText?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(CommonR.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}
