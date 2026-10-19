package dev.yashgarg.qbit.ui.torrent.tabs

import qbittorrent.models.PieceState

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
