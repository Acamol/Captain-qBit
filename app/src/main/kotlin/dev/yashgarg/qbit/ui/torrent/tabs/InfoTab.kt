package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.ListTile
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.rememberCopyToClipboard
import dev.yashgarg.qbit.utils.toDate
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.toTime

@Composable
fun InfoTab(state: TorrentDetailsState, modifier: Modifier = Modifier) {
    val torrent = state.torrent
    val props = state.torrentProperties
    if (state.loading || torrent == null || props == null) {
        CenterLinearLoading(modifier.fillMaxSize(), R.color.md_theme_dark_seed)
        return
    }

    val infinite = stringResource(CommonR.string.infinite)
    val unspecified = stringResource(CommonR.string.unspecified)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Transfer") {
            Row(
                "Connections",
                stringResource(
                    CommonR.string.connections_sub,
                    props.nbConnections,
                    props.nbConnectionsLimit,
                ),
            )
            Row("Seeds", stringResource(CommonR.string.sp_sub, props.seeds, props.seedsTotal))
            Row("Peers", stringResource(CommonR.string.sp_sub, props.peers, props.peersTotal))
            Row("Time active", props.timeElapsed.toTime())
            Row("ETA", if (props.eta == 8640000L) infinite else props.eta.toTime())
            Row(
                "Downloaded",
                stringResource(
                    CommonR.string.dl_up_sub,
                    props.totalDownloaded.toHumanReadable(),
                    props.totalDownloadedSession.toHumanReadable(),
                ),
            )
            Row(
                "Uploaded",
                stringResource(
                    CommonR.string.dl_up_sub,
                    props.totalUploaded.toHumanReadable(),
                    props.totalUploadedSession.toHumanReadable(),
                ),
            )
            Row(
                "Down speed",
                stringResource(
                    CommonR.string.dl_up_speed_sub,
                    props.dlSpeed.toHumanReadable(),
                    props.dlSpeedAvg.toHumanReadable(),
                ),
            )
            Row(
                "Up speed",
                stringResource(
                    CommonR.string.dl_up_speed_sub,
                    props.upSpeed.toHumanReadable(),
                    props.upSpeedAvg.toHumanReadable(),
                ),
            )
            Row("Down limit", props.dlLimit.toHumanReadable())
            Row("Up limit", props.upLimit.toHumanReadable())
            Row("Wasted", props.totalWasted.toHumanReadable())
            Row("Ratio", "%.2f".format(props.shareRatio))
            Row("Re-announce", if (props.reannounce == 0L) infinite else props.reannounce.toTime())
            Row("Last seen complete", props.lastSeen.toDate())
            Row("Priority", torrent.priority.toString())
        }

        SectionCard("Torrent info") {
            Row("Total size", props.totalSize.toHumanReadable())
            Row("Created by", props.createdBy.ifEmpty { unspecified })
            Row("Added on", props.additionDate.toDate())
            Row("Completed on", props.completionDate.toDate())
            Row("Created on", props.creationDate.toDate())
            Row("Save path", props.savePath)
            Row("Category", torrent.category.ifEmpty { unspecified })
            Row("Hash", torrent.hash)
            Row("Comment", props.comment.ifEmpty { unspecified })
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    val copy = rememberCopyToClipboard()
    ListTile(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        title = label,
        subtitle = value,
        // Long-press any field to copy its value.
        onLongClick = { copy(label, value, "Copied $label") },
    )
}
