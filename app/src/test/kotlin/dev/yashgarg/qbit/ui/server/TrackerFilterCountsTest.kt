package dev.yashgarg.qbit.ui.server

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerFilterCountsTest {
    @Test
    fun `counts use case insensitive hosts across tracker URLs`() {
        val counts =
            trackerFilterCounts(
                listOf(
                    "https://Tracker.example/announce",
                    "udp://tracker.example:6969/announce",
                    "https://other.example/announce",
                    "https://Tracker.example/announce",
                ),
            ) {
                URI(it).host
            }

        assertEquals(
            mapOf("Tracker.example" to 3, "other.example" to 1, "tracker.example" to 3),
            counts,
        )
        assertEquals(
            listOf("Tracker.example", "other.example", "tracker.example"),
            counts.keys.toList(),
        )
    }

    @Test
    fun `each distinct URL is parsed once even when many torrents share it`() {
        val calls = mutableListOf<String>()
        val counts =
            trackerFilterCounts(
                List(10_000) { "https://tracker.example/announce" } +
                    listOf("", " ", "invalid", "invalid"),
            ) {
                calls.add(it)
                URI(it).host
            }

        assertEquals(mapOf("tracker.example" to 10_000), counts)
        assertEquals(listOf("https://tracker.example/announce", "invalid"), calls)
    }
}
