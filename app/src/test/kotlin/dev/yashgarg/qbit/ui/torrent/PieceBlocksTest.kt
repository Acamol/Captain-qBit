package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.ui.torrent.tabs.BlockState
import dev.yashgarg.qbit.ui.torrent.tabs.groupPieces
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.PieceState

class PieceBlocksTest {
    private val m = PieceState.MISSING
    private val d = PieceState.DOWNLOADING
    private val x = PieceState.DONE

    @Test
    fun decodesQbittorrentIntegerStates() {
        val decoded = Json.decodeFromString<List<PieceState>>("[0,1,2,2,7]")
        assertEquals(listOf(m, d, x, x, m), decoded)
    }

    @Test
    fun smallTorrentsKeepOneBlockPerPiece() {
        val result = groupPieces(listOf(m, d, x), maxBlocks = 10)
        assertEquals(1, result.piecesPerBlock)
        assertEquals(
            listOf(BlockState.MISSING, BlockState.DOWNLOADING, BlockState.DONE),
            result.blocks,
        )
    }

    @Test
    fun largeTorrentsAreGroupedWithoutExceedingTheCap() {
        val result = groupPieces(listOf(x, x, m, m, d, m, x, m, m), maxBlocks = 4)
        assertEquals(3, result.piecesPerBlock)
        assertEquals(
            listOf(BlockState.PARTIAL, BlockState.DOWNLOADING, BlockState.PARTIAL),
            result.blocks,
        )
    }

    @Test
    fun allDoneGroupIsDoneAndEmptyHasNoBlocks() {
        assertEquals(listOf(BlockState.DONE), groupPieces(listOf(x, x, x), maxBlocks = 1).blocks)
        assertEquals(emptyList<BlockState>(), groupPieces(emptyList(), maxBlocks = 4).blocks)
    }
}
