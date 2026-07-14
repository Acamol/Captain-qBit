package dev.yashgarg.qbit.ui.torrent.tabs

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.TorrentPeersFragmentBinding
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.ListTile
import dev.yashgarg.qbit.ui.compose.theme.AppTypography
import dev.yashgarg.qbit.ui.compose.theme.bodyMediumPrimary
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsViewModel
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.utils.CountryFlags
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.viewBinding
import qbittorrent.models.TorrentPeer

class TorrentPeersFragment : Fragment(R.layout.torrent_peers_fragment) {
    private val binding by viewBinding(TorrentPeersFragmentBinding::bind)
    private val viewModel by
        viewModels<TorrentDetailsViewModel>(ownerProducer = { requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.peersComposeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
        )
        @OptIn(ExperimentalComposeUiApi::class)
        binding.peersComposeView.setContent {
            val state by viewModel.uiState.collectAsState()
            val scrollState = rememberNestedScrollInteropConnection()

            QbitComposeTheme {
                PeersListView(
                    state,
                    Modifier.nestedScroll(scrollState),
                    onBan = { viewModel.banPeer(it) },
                )
            }
        }
    }
}

@Composable
fun PeersListView(
    state: TorrentDetailsState,
    modifier: Modifier = Modifier,
    onBan: (TorrentPeer) -> Unit,
) {
    if (state.peersLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.peers == null || state.peers.peers.isEmpty()) {
        Center(modifier) { Text("No peers connected") }
    } else {
        val peers = requireNotNull(state.peers).peers.values.toList()
        LazyColumn(modifier) {
            itemsIndexed(peers, key = { pos, peer -> "${peer.ip}-$pos" }) { _, peer ->
                val openDialog = remember { mutableStateOf(false) }
                val context = LocalContext.current

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
                    onClick = { openDialog.value = true },
                    onLongClick = {
                        ClipboardUtil.copyToClipboard(
                            context,
                            "peer_${peer.ip}",
                            "${peer.ip}:${peer.port}",
                        )
                    },
                )

                if (openDialog.value) {
                    PeerDetailsDialog(
                        peer = peer,
                        onBan = {
                            onBan(peer)
                            openDialog.value = false
                        },
                        onDismiss = { openDialog.value = false },
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
                    PeerDetailRow("Country", "${peer.country} $flag".trim())
                }
                if (peer.connection.isNotBlank()) PeerDetailRow("Connection", peer.connection)
                if (peer.client.isNotBlank()) PeerDetailRow("Client", peer.client)
                PeerDetailRow("Progress", percent(peer.progress))
                PeerDetailRow("Down speed", "${peer.dlSpeed.toHumanReadable()}/s")
                PeerDetailRow("Up speed", "${peer.upSpeed.toHumanReadable()}/s")
                PeerDetailRow("Downloaded", peer.downloaded.toHumanReadable())
                PeerDetailRow("Uploaded", peer.uploaded.toHumanReadable())
                PeerDetailRow("Relevance", percent(peer.relevance))
                val flags =
                    listOf(peer.flags, peer.flagsDesc)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                if (flags.isNotBlank()) PeerDetailRow("Flags", flags)
                if (peer.files.isNotBlank()) PeerDetailRow("Files", peer.files)
            }
        },
        confirmButton = {
            TextButton(onClick = onBan) { Text("Ban Peer", style = bodyMediumPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss", style = bodyMediumPrimary) }
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
