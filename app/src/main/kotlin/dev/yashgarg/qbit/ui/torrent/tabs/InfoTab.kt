package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.server.TorrentTransferSummary
import dev.yashgarg.qbit.ui.server.labelRes
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.isolateLtr
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.utils.toDate
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.toTime

@Composable
fun InfoTab(state: TorrentDetailsState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val torrent = state.torrent
    val props = state.torrentProperties
    if (state.loading) {
        CenterLinearLoading(modifier.fillMaxSize(), R.color.md_theme_dark_seed)
        return
    }
    if (torrent == null || props == null) {
        Center(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    state.error?.message
                        ?: stringResource(CommonR.string.status_load_torrent_details_failure),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(CommonR.string.retry_action))
                }
            }
        }
        return
    }

    val infinite = stringResource(CommonR.string.infinite)
    val unspecified = stringResource(CommonR.string.unspecified)
    val session = stringResource(CommonR.string.details_session_value)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(torrent.state.labelRes()),
                    style = MaterialTheme.typography.titleLarge,
                )
                TorrentTransferSummary(torrent, showEta = false)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SwarmMetric(
                    stringResource(CommonR.string.seeds),
                    "${props.seeds} / ${props.seedsTotal}",
                    Modifier.weight(1f),
                )
                SwarmMetric(
                    stringResource(CommonR.string.peers),
                    "${props.peers} / ${props.peersTotal}",
                    Modifier.weight(1f),
                )
                SwarmMetric(
                    stringResource(CommonR.string.ratio),
                    "%.2f".format(props.shareRatio),
                    Modifier.weight(1f),
                )
            }
        }
        DetailSection(
            stringResource(CommonR.string.details_activity_title),
            Icons.Filled.Schedule,
            listOf(
                DetailField(
                    stringResource(CommonR.string.eta),
                    if (props.eta == 8640000L) infinite else props.eta.toTime(),
                ),
                DetailField(stringResource(CommonR.string.time_active), props.timeElapsed.toTime()),
                DetailField(
                    stringResource(CommonR.string.added_on),
                    props.additionDate.knownDate(unspecified),
                ),
                DetailField(
                    stringResource(CommonR.string.completed_on),
                    props.completionDate.knownDate(unspecified),
                ),
                DetailField(
                    stringResource(CommonR.string.last_complete),
                    props.lastSeen.knownDate(unspecified),
                ),
            ),
        )
        DetailSection(
            stringResource(CommonR.string.details_data_title),
            Icons.Filled.SwapVert,
            listOf(
                DetailField(
                    stringResource(CommonR.string.downloaded),
                    props.totalDownloaded.toHumanReadable(),
                    session.format(props.totalDownloadedSession.toHumanReadable()),
                ),
                DetailField(
                    stringResource(CommonR.string.uploaded),
                    props.totalUploaded.toHumanReadable(),
                    session.format(props.totalUploadedSession.toHumanReadable()),
                ),
                DetailField(
                    stringResource(CommonR.string.wasted),
                    props.totalWasted.toHumanReadable(),
                ),
            ),
        )
        DetailSection(
            stringResource(CommonR.string.details_bandwidth_title),
            Icons.Filled.Speed,
            listOf(
                DetailField(
                    stringResource(CommonR.string.dl_speed),
                    stringResource(CommonR.string.transfer_rate, props.dlSpeed.toHumanReadable())
                        .isolateLtr(),
                    stringResource(CommonR.string.details_average_download) +
                        ": " +
                        stringResource(
                                CommonR.string.transfer_rate,
                                props.dlSpeedAvg.toHumanReadable(),
                            )
                            .isolateLtr(),
                ),
                DetailField(
                    stringResource(CommonR.string.up_speed),
                    stringResource(CommonR.string.transfer_rate, props.upSpeed.toHumanReadable())
                        .isolateLtr(),
                    stringResource(CommonR.string.details_average_upload) +
                        ": " +
                        stringResource(
                                CommonR.string.transfer_rate,
                                props.upSpeedAvg.toHumanReadable(),
                            )
                            .isolateLtr(),
                ),
                DetailField(
                    stringResource(CommonR.string.dl_limit),
                    if (props.dlLimit < 0) infinite else props.dlLimit.toHumanReadable(),
                ),
                DetailField(
                    stringResource(CommonR.string.up_limit),
                    if (props.upLimit < 0) infinite else props.upLimit.toHumanReadable(),
                ),
                DetailField(
                    stringResource(CommonR.string.connections),
                    stringResource(
                        CommonR.string.connections_sub,
                        props.nbConnections,
                        props.nbConnectionsLimit,
                    ),
                ),
                DetailField(
                    stringResource(CommonR.string.reannounce),
                    if (props.reannounce == 0L) infinite else props.reannounce.toTime(),
                ),
                DetailField(stringResource(CommonR.string.priority), torrent.priority.toString()),
            ),
        )
        DetailSection(
            stringResource(CommonR.string.details_storage_title),
            Icons.Filled.Folder,
            listOf(
                DetailField(
                    stringResource(CommonR.string.total_size),
                    props.totalSize.toHumanReadable(),
                ),
                DetailField(
                    stringResource(CommonR.string.save_path),
                    props.savePath,
                    stacked = true,
                ),
                DetailField(
                    stringResource(CommonR.string.category),
                    torrent.category.ifEmpty { unspecified },
                    stacked = true,
                ),
            ),
        )
        DetailSection(
            stringResource(CommonR.string.torrent_info),
            Icons.Filled.Info,
            listOf(
                DetailField(
                    stringResource(CommonR.string.torrent_hash),
                    torrent.hash,
                    stacked = true,
                ),
                DetailField(
                    stringResource(CommonR.string.created_by),
                    props.createdBy.ifEmpty { unspecified },
                    stacked = true,
                ),
                DetailField(
                    stringResource(CommonR.string.created_on),
                    props.creationDate.knownDate(unspecified),
                ),
                DetailField(
                    stringResource(CommonR.string.comment),
                    props.comment.ifEmpty { unspecified },
                    stacked = true,
                ),
            ),
        )
    }
}

@Composable
private fun SwarmMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val copy = rememberCopyToClipboard()
    val copied = stringResource(CommonR.string.clipboard_copied)
    val copyValue = { copy(label, value, copied) }
    Column(
        modifier.combinedClickable(
            onClick = copyValue,
            onLongClick = copyValue,
            onLongClickLabel = stringResource(CommonR.string.copy_action),
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value.isolateLtr(), style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Long.knownDate(unspecified: String): String = if (this > 0) toDate() else unspecified
