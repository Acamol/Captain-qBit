package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.ui.torrent.tabs.BlockState
import dev.yashgarg.qbit.ui.torrent.tabs.byteRange
import dev.yashgarg.qbit.ui.torrent.tabs.filesIn
import dev.yashgarg.qbit.ui.torrent.tabs.groupPieces
import dev.yashgarg.qbit.ui.torrent.tabs.piecesIn
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.PieceState
import qbittorrent.models.TorrentFile

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

    @Test
    fun lastBlockCoversOnlyTheRemainingPieces() {
        val grouped = groupPieces(List(10) { m }, maxBlocks = 4)
        assertEquals(0..2, grouped.piecesIn(0, totalPieces = 10))
        assertEquals(9..9, grouped.piecesIn(3, totalPieces = 10))
    }

    @Test
    fun byteRangeIsCappedAtTheShorterLastPiece() {
        assertEquals(4L..7L, byteRange(1..1, pieceSize = 4, totalSize = 10))
        assertEquals(8L..9L, byteRange(2..2, pieceSize = 4, totalSize = 10))
    }

    @Test
    fun piecesOnAFileBoundaryNameEveryFileTheyTouch() {
        val files = listOf(file(1, 3..5), file(0, 0..3), file(2, 6..9))
        assertEquals(listOf(0, 1), filesIn(3..3, files).map { it.index })
        assertEquals(listOf(1, 2), filesIn(5..6, files).map { it.index })
        assertEquals(emptyList<Int>(), filesIn(10..11, files).map { it.index })
    }

    private fun file(index: Int, pieces: IntRange) =
        TorrentFile(
            index = index,
            name = "file$index",
            size = 1,
            progress = 0f,
            priority = 1,
            pieceRange = listOf(pieces.first, pieces.last),
            availability = 0f,
        )
}
