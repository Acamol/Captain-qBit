package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.isolateLtr
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.toTime
import qbittorrent.models.Torrent

@Composable
internal fun TorrentTransferSummary(
    torrent: Torrent,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    showEta: Boolean = true,
    compact: Boolean = false,
) {
    val downloading =
        torrent.state == Torrent.State.DOWNLOADING || torrent.state == Torrent.State.FORCED_DL
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    CommonR.string.torrent_progress_percent,
                    (torrent.progress * 100).toInt(),
                ),
                style =
                    if (compact) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(
                            CommonR.string.torrent_completed_size,
                            torrent.completed.toHumanReadable(),
                            torrent.size.toHumanReadable(),
                        )
                        .isolateLtr(),
                    style =
                        if (compact) MaterialTheme.typography.labelMedium
                        else MaterialTheme.typography.labelLarge,
                )
                if (torrent.amountLeft > 0) {
                    Text(
                        stringResource(
                            CommonR.string.torrent_remaining_size,
                            torrent.amountLeft.toHumanReadable(),
                        ),
                        style =
                            if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                }
            }
        }
        if (downloading) {
            LinearWavyProgressIndicator(
                progress = { torrent.progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                waveSpeed =
                    WavyProgressIndicatorDefaults.LinearDeterminateWavelength *
                        downloadWaveCyclesPerSecond(torrent.dlspeed, torrent.size),
            )
        } else {
            LinearProgressIndicator(
                progress = { torrent.progress },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = if (compact) 6.dp else 12.dp)
                        .height(if (compact) 4.dp else 8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compact) {
                CompactTransferMetric(
                    label = stringResource(CommonR.string.dl_speed),
                    speed = torrent.dlspeed,
                    icon = Icons.Filled.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )
                CompactTransferMetric(
                    label = stringResource(CommonR.string.up_speed),
                    speed = torrent.uploadSpeed,
                    icon = Icons.Filled.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
            } else {
                TransferMetric(
                    label = stringResource(CommonR.string.dl_speed),
                    value =
                        stringResource(
                            CommonR.string.transfer_rate,
                            torrent.dlspeed.toHumanReadable(),
                        ),
                    icon = Icons.Filled.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )
                TransferMetric(
                    label = stringResource(CommonR.string.up_speed),
                    value =
                        stringResource(
                            CommonR.string.transfer_rate,
                            torrent.uploadSpeed.toHumanReadable(),
                        ),
                    icon = Icons.Filled.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (downloading && showEta && torrent.eta != 8640000L) {
            Text(
                torrent.eta.toTime(),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun CompactTransferMetric(
    label: String,
    speed: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(CommonR.string.transfer_rate, speed.toHumanReadable()).isolateLtr(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
