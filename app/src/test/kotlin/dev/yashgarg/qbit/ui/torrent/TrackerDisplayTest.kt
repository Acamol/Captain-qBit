package dev.yashgarg.qbit.ui.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import qbittorrent.models.TorrentTracker

class TrackerDisplayTest {
    private fun tracker(url: String, status: Int = 2) =
        TorrentTracker(
            url = url,
            status = status,
            tier = 0,
            numPeers = 0,
            numSeeds = 0,
            numLeeches = 0,
            numDownloaded = 0,
        )

    @Test
    fun `a real tracker is named by its host`() {
        assertEquals(
            "tracker.archlinux.org",
            tracker("https://tracker.archlinux.org:443/announce").displayName(),
        )
    }

    @Test
    fun `a url without a host falls back to the whole url`() {
        assertEquals("udp//broken", tracker("udp//broken").displayName())
    }

    @Test
    fun `a pseudo-source is named without its decoration`() {
        assertEquals("DHT", tracker("** [DHT] **").displayName())
        assertEquals("PeX", tracker("** [PeX] **").displayName())
    }

    @Test
    fun `unreported figures are -1 and show as unknown`() {
        assertNull((-1).reportedOrNull())
        assertEquals(0, 0.reportedOrNull())
        assertEquals(12, 12.reportedOrNull())
    }

    @Test
    fun `the summary counts real trackers and ignores pseudo-sources`() {
        val summary =
            listOf(
                    tracker("** [DHT] **"),
                    tracker("https://a/announce", status = 2),
                    tracker("https://b/announce", status = 4),
                    tracker("https://c/announce", status = 1),
                )
                .summary()

        assertEquals(TrackerSummary(total = 3, working = 1, notWorking = 1), summary)
    }
}
