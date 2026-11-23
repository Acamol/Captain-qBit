package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.data.models.TrackerStatus
import java.net.URI
import qbittorrent.models.TorrentTracker

// qBittorrent lists the DHT/PeX/LSD pseudo-sources as trackers whose URL is bracketed like
// "** [DHT] **"; those aren't real URLs and can't be edited or removed.
fun TorrentTracker.isPseudoSource(): Boolean = url.startsWith("**")

/** The card title: a real tracker's host, or a pseudo-source's bare name ("DHT"). */
fun TorrentTracker.displayName(): String =
    if (isPseudoSource()) {
        url.trim('*', ' ', '[', ']')
    } else {
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
    }

/**
 * The count to show for a tracker figure; qBittorrent reports -1 until the tracker has answered.
 */
fun Int.reportedOrNull(): Int? = takeIf { it >= 0 }

data class TrackerSummary(val total: Int, val working: Int, val notWorking: Int)

/** Health of the real trackers only; DHT/PeX/LSD aren't counted. */
fun List<TorrentTracker>.summary(): TrackerSummary {
    val real = filterNot { it.isPseudoSource() }
    return TrackerSummary(
        total = real.size,
        working =
            real.count { TrackerStatus.statusOf(it.status) == TrackerStatus.CONTACTED_WORKING },
        notWorking =
            real.count { TrackerStatus.statusOf(it.status) == TrackerStatus.CONTACTED_NOT_WORKING },
    )
}
