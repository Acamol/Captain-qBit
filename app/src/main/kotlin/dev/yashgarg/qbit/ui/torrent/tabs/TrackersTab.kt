package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.data.models.TrackerStatus
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState

@Composable
fun TrackersTab(state: TorrentDetailsState, modifier: Modifier = Modifier) {
    val trackers = state.trackers
    if (trackers.isEmpty()) {
        Center(modifier.fillMaxSize()) { Text("No trackers") }
        return
    }

    val green = colorResource(R.color.green)
    val yellow = colorResource(R.color.yellow)
    val red = colorResource(R.color.red)

    LazyColumn(modifier.fillMaxSize()) {
        itemsIndexed(trackers) { _, tracker ->
            val color: Color =
                when (TrackerStatus.statusOf(tracker.status)) {
                    TrackerStatus.CONTACTED_WORKING -> green
                    TrackerStatus.UPDATING -> yellow
                    else -> red
                }
            Text(
                text = tracker.url,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}
