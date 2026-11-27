package dev.yashgarg.qbit.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import qbittorrent.models.MainData
import qbittorrent.models.Torrent

class StateFilterTest {

    private fun torrent(name: String): Torrent =
        Torrent(
            addedOn = 0L,
            amountLeft = 0L,
            autoTmm = false,
            availability = 0f,
            category = "",
            completed = 0L,
            completedOn = 0L,
            contentPath = "",
            dlLimit = -1L,
            dlspeed = 0L,
            downloaded = 0f,
            downloadedSession = 0f,
            eta = 0L,
            firstLastPiecePriority = false,
            forceStart = false,
            hash = "hash",
            lastActivity = 0L,
            magnetUri = "",
            maxRatio = -1f,
            maxSeedingTime = -1L,
            name = name,
            seedsInSwarm = 0,
            leechersInSwarm = 0,
            connectedLeechers = 0,
            connectedSeeds = 0,
            priority = 0,
            progress = 1f,
            ratio = 1f,
            ratioLimit = -1f,
            savePath = "",
            seedingTimeLimit = -1L,
            seenCompleted = 0L,
            sequentialDownload = false,
            size = 1000L,
            state = Torrent.State.UPLOADING,
            superSeeding = false,
            tags = emptyList(),
            timeActive = 0L,
            seedingTime = 0L,
            totalSize = 1000L,
            tracker = "",
            uploadLimit = -1L,
            uploaded = 0L,
            uploadedSession = 0L,
            uploadSpeed = 0L,
        )

    @Test
    fun `blank query matches everything`() {
        assertTrue(torrent("Rick and Morty S01").matchesSearch(""))
        assertTrue(torrent("Rick and Morty S01").matchesSearch("   "))
    }

    @Test
    fun `single word matches a substring anywhere in the name`() {
        assertTrue(torrent("Rick and Morty S01").matchesSearch("morty"))
    }

    @Test
    fun `multiple words match regardless of order`() {
        assertTrue(torrent("Rick and Morty S01").matchesSearch("morty rick"))
    }

    @Test
    fun `all words must be present`() {
        assertFalse(torrent("Rick and Morty S01").matchesSearch("rick breaking bad"))
    }

    @Test
    fun `does not require the query as one contiguous substring`() {
        // "rick s01" never appears contiguously in the name, but both words do independently.
        assertTrue(torrent("Rick and Morty S01").matchesSearch("rick s01"))
    }

    @Test
    fun `is case-insensitive`() {
        assertTrue(torrent("Rick and Morty S01").matchesSearch("RICK MORTY"))
    }

    @Test
    fun `actions and open dialogs lose deleted and newly filtered torrents without changing the filter`() {
        val a = torrent("one").copy(hash = "a", category = "linux")
        val b = a.copy(hash = "b")
        val c = a.copy(hash = "c")
        val requested = listOf(a.hash, b.hash, c.hash)
        val data =
            MainData(1L, torrents = listOf(a, b, c).associateBy { it.hash }, serverState = mock())
        val initial =
            ServerScreenState(dataLoading = false, data = data, selectedCategory = "linux")
        assertEquals(requested, initial.visibleTargets(requested))

        val live =
            initial.copy(
                data = data.copy(torrents = mapOf("a" to a, "b" to b.copy(category = "other"))),
            )
        assertEquals(listOf("a"), live.visibleTargets(requested))
        assertEquals(
            ServerDialog.BulkTags(listOf("a")),
            ServerDialog.BulkTags(requested).reconcileTargets(live),
        )
        assertEquals(
            ServerDialog.CreateTag(listOf("a")),
            ServerDialog.CreateTag(requested).reconcileTargets(live),
        )
        assertNull(ServerDialog.BulkCategory(listOf("b", "c")).reconcileTargets(live))
        assertTrue(live.copy(dataLoading = true).visibleTargets(requested).isEmpty())
    }
}
