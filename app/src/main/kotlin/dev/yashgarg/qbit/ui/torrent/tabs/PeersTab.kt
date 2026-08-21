package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.ListTile
import dev.yashgarg.qbit.ui.compose.theme.AppTypography
import dev.yashgarg.qbit.ui.compose.theme.bodyMediumPrimary
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.CountryFlags
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
        LazyColumn(modifier) {
            itemsIndexed(peers, key = { pos, peer -> "${peer.ip}-$pos" }) { _, peer ->
                var openDialog by remember { mutableStateOf(false) }
                val copy = rememberCopyToClipboard()
                val copiedToClipboardMessage = stringResource(CommonR.string.clipboard_copied)

                ListTile(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    title = "${peer.ip}:${peer.port}",
                    subtitle =
                        "↓ ${peer.dlSpeed.toHumanReadable()}/s   ↑ ${peer.upSpeed.toHumanReadable()}/s" +
                            " · ${percent(peer.progress)}",
                    suffix = {
                        Text(
                            CountryFlags.getCountryFlagByCountryCode(peer.countryCode),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    onClick = { openDialog = true },
                    onLongClick = {
                        copy("peer_${peer.ip}", "${peer.ip}:${peer.port}", copiedToClipboardMessage)
                    },
                )

                if (openDialog) {
                    PeerDetailsDialog(
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
private fun PeerDetailsDialog(peer: TorrentPeer, onBan: () -> Unit, onDismiss: () -> Unit) {
    val flag = CountryFlags.getCountryFlagByCountryCode(peer.countryCode)
    AlertDialog(
        tonalElevation = 5.dp,
        onDismissRequest = onDismiss,
        title = { Text("${peer.ip}:${peer.port}", style = AppTypography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (peer.country.isNotBlank()) {
                    PeerDetailRow(
                        stringResource(CommonR.string.peer_country_label),
                        "${peer.country} $flag".trim(),
                    )
                }
                if (peer.connection.isNotBlank()) {
                    PeerDetailRow(
                        stringResource(CommonR.string.peer_connection_label),
                        peer.connection,
                    )
                }
                if (peer.client.isNotBlank()) {
                    PeerDetailRow(stringResource(CommonR.string.peer_client_label), peer.client)
                }
                PeerDetailRow(stringResource(CommonR.string.sort_progress), percent(peer.progress))
                PeerDetailRow(
                    stringResource(CommonR.string.peer_down_speed_label),
                    "${peer.dlSpeed.toHumanReadable()}/s",
                )
                PeerDetailRow(
                    stringResource(CommonR.string.peer_up_speed_label),
                    "${peer.upSpeed.toHumanReadable()}/s",
                )
                PeerDetailRow(
                    stringResource(CommonR.string.downloaded),
                    peer.downloaded.toHumanReadable(),
                )
                PeerDetailRow(
                    stringResource(CommonR.string.uploaded),
                    peer.uploaded.toHumanReadable(),
                )
                PeerDetailRow(
                    stringResource(CommonR.string.peer_relevance_label),
                    percent(peer.relevance),
                )
                val flags =
                    listOf(peer.flags, peer.flagsDesc)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                if (flags.isNotBlank()) {
                    PeerDetailRow(stringResource(CommonR.string.peer_flags_label), flags)
                }
                if (peer.files.isNotBlank()) {
                    PeerDetailRow(stringResource(CommonR.string.peer_files_label), peer.files)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBan) {
                Text(stringResource(CommonR.string.ban_peer_action), style = bodyMediumPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.dismiss_action), style = bodyMediumPrimary)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PeerDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style =
                AppTypography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(
            value,
            style = AppTypography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Formats a 0..1 progress/relevance fraction as a percentage, e.g. 0.535 -> "53.5%". */
private fun percent(fraction: Float): String = "%.1f%%".format(fraction * 100)
