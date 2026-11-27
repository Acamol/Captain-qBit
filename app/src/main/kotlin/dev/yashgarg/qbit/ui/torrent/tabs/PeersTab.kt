package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.ActionSheet
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.CountryFlags
import dev.yashgarg.qbit.utils.isolateLtr
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.utils.toHumanReadable
import qbittorrent.models.TorrentPeer

@Composable
fun PeersListView(
    state: TorrentDetailsState,
    modifier: Modifier = Modifier,
    onBan: (TorrentPeer) -> Unit,
) {
    if (state.peersLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.peers == null || state.peers.peers.isEmpty()) {
        Center(modifier) { Text(stringResource(CommonR.string.no_peers_connected)) }
    } else {
        val peers = requireNotNull(state.peers).peers.values.toList()
        LazyColumn(
            modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(CommonR.string.peers),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(peers.size.toString())
                    }
                }
            }
            itemsIndexed(peers, key = { pos, peer -> "${peer.ip}-$pos" }) { index, peer ->
                var openDialog by remember { mutableStateOf(false) }
                val copy = rememberCopyToClipboard()
                val copiedToClipboardMessage = stringResource(CommonR.string.clipboard_copied)

                val flag = CountryFlags.getCountryFlagByCountryCode(peer.countryCode)
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index, peers.size),
                    leadingContent =
                        if (flag.isNotBlank()) {
                            { Text(flag, style = MaterialTheme.typography.titleLarge) }
                        } else null,
                    overlineContent =
                        if (peer.client.isNotBlank()) {
                            {
                                Text(
                                    peer.client,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        } else null,
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "↓ " +
                                        stringResource(
                                                CommonR.string.transfer_rate,
                                                peer.dlSpeed.toHumanReadable(),
                                            )
                                            .isolateLtr(),
                                )
                                Text(
                                    "↑ " +
                                        stringResource(
                                                CommonR.string.transfer_rate,
                                                peer.upSpeed.toHumanReadable(),
                                            )
                                            .isolateLtr(),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { peer.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                    },
                    trailingContent = { Text(percent(peer.progress)) },
                    onClick = { openDialog = true },
                    onLongClickLabel = stringResource(CommonR.string.copy_action),
                    onLongClick = {
                        copy("peer_${peer.ip}", "${peer.ip}:${peer.port}", copiedToClipboardMessage)
                    },
                ) {
                    Text(
                        "${peer.ip}:${peer.port}".isolateLtr(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (openDialog) {
                    PeerDetailsSheet(
                        peer = peer,
                        onBan = {
                            onBan(peer)
                            openDialog = false
                        },
                        onDismiss = { openDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerDetailsSheet(peer: TorrentPeer, onBan: () -> Unit, onDismiss: () -> Unit) {
    val flag = CountryFlags.getCountryFlagByCountryCode(peer.countryCode)
    ActionSheet(
        onDismissRequest = onDismiss,
        title = "${peer.ip}:${peer.port}".isolateLtr(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (peer.country.isNotBlank()) {
                DetailRow(
                    stringResource(CommonR.string.peer_country_label),
                    "${peer.country} $flag".trim(),
                )
            }
            if (peer.connection.isNotBlank()) {
                DetailRow(
                    stringResource(CommonR.string.peer_connection_label),
                    peer.connection,
                )
            }
            if (peer.client.isNotBlank()) {
                DetailRow(stringResource(CommonR.string.peer_client_label), peer.client)
            }
            DetailRow(stringResource(CommonR.string.sort_progress), percent(peer.progress))
            DetailRow(
                stringResource(CommonR.string.peer_down_speed_label),
                stringResource(CommonR.string.transfer_rate, peer.dlSpeed.toHumanReadable())
                    .isolateLtr(),
            )
            DetailRow(
                stringResource(CommonR.string.peer_up_speed_label),
                stringResource(CommonR.string.transfer_rate, peer.upSpeed.toHumanReadable())
                    .isolateLtr(),
            )
            DetailRow(
                stringResource(CommonR.string.downloaded),
                peer.downloaded.toHumanReadable(),
            )
            DetailRow(
                stringResource(CommonR.string.uploaded),
                peer.uploaded.toHumanReadable(),
            )
            DetailRow(
                stringResource(CommonR.string.peer_relevance_label),
                percent(peer.relevance),
            )
            val flags =
                listOf(peer.flags, peer.flagsDesc).filter { it.isNotBlank() }.joinToString(" — ")
            if (flags.isNotBlank()) {
                DetailRow(stringResource(CommonR.string.peer_flags_label), flags)
            }
            if (peer.files.isNotBlank()) {
                DetailRow(stringResource(CommonR.string.peer_files_label), peer.files)
            }
        }
        TextButton(
            onClick = onBan,
            modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.End),
        ) {
            Text(
                stringResource(CommonR.string.ban_peer_action),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A label/value line in peer and piece detail sheets. */
@Composable
internal fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Formats a 0..1 progress/relevance fraction as a percentage, e.g. 0.535 -> "53.5%". */
private fun percent(fraction: Float): String = "%.1f%%".format(fraction * 100)
