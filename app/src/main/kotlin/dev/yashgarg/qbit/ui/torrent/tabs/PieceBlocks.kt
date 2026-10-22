package dev.yashgarg.qbit.ui.torrent.tabs

import qbittorrent.models.PieceState
import qbittorrent.models.TorrentFile

/** One drawn cell of the piece map; [PARTIAL] only appears when several pieces share a cell. */
enum class BlockState {
    MISSING,
    PARTIAL,
    DOWNLOADING,
    DONE,
}

/** The pieces and the blocks they were grouped into, [piecesPerBlock] pieces per block. */
data class PieceBlocks(val blocks: List<BlockState>, val piecesPerBlock: Int)

/**
 * Groups [pieces] into at most [maxBlocks] blocks so huge torrents stay drawable. A block is done
 * only when all its pieces are, downloading when any is, partial when some (not all) are done.
 */
fun groupPieces(pieces: List<PieceState>, maxBlocks: Int): PieceBlocks {
    val perBlock = if (pieces.isEmpty()) 1 else (pieces.size + maxBlocks - 1) / maxBlocks
    val blocks =
        pieces.chunked(perBlock).map { group ->
            when {
                group.all { it == PieceState.DONE } -> BlockState.DONE
                group.any { it == PieceState.DOWNLOADING } -> BlockState.DOWNLOADING
                group.any { it == PieceState.DONE } -> BlockState.PARTIAL
                else -> BlockState.MISSING
            }
        }
    return PieceBlocks(blocks, perBlock)
}

/** The 0-based piece indices that block [blockIndex] covers; the last block may hold fewer. */
fun PieceBlocks.piecesIn(blockIndex: Int, totalPieces: Int): IntRange {
    val first = blockIndex * piecesPerBlock
    return first until minOf(first + piecesPerBlock, totalPieces)
}

/**
 * Where [pieces] sit in the torrent's data, as an inclusive byte span. The torrent's last piece is
 * usually shorter than [pieceSize], so the span is capped at [totalSize].
 */
fun byteRange(pieces: IntRange, pieceSize: Long, totalSize: Long): LongRange =
    pieces.first * pieceSize..minOf((pieces.last + 1L) * pieceSize, totalSize) - 1

/** The files whose data overlaps [pieces] — several when the pieces straddle a file boundary. */
fun filesIn(pieces: IntRange, files: List<TorrentFile>): List<TorrentFile> =
    files
        .filter { file ->
            val span = file.pieceRange
            span.size == 2 && span[0] <= pieces.last && span[1] >= pieces.first
        }
        .sortedBy { it.index }
