package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.toHumanReadable
import qbittorrent.models.PieceState

// Beyond this many blocks the grid gets too tall to read and slow to draw, so pieces are grouped.
private const val MAX_BLOCKS = 4096
private val BLOCK_SIZE = 10.dp
private val BLOCK_GAP = 2.dp

@Composable
fun PiecesTab(state: TorrentDetailsState, modifier: Modifier = Modifier) {
    val pieces = state.pieceStates
    if (pieces.isEmpty()) {
        if (state.piecesLoading) {
            CenterLinearLoading(modifier.fillMaxSize(), R.color.md_theme_dark_seed)
        } else {
            Text(
                stringResource(CommonR.string.pieces_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier.fillMaxWidth().padding(16.dp),
            )
        }
        return
    }

    val grouped = remember(pieces) { groupPieces(pieces, MAX_BLOCKS) }
    val done = remember(pieces) { pieces.count { it == PieceState.DONE } }
    val green = colorResource(R.color.green)
    val colors =
        mapOf(
            BlockState.DONE to green,
            BlockState.PARTIAL to green.copy(alpha = 0.45f),
            BlockState.DOWNLOADING to colorResource(R.color.yellow),
            BlockState.MISSING to MaterialTheme.colorScheme.outlineVariant,
        )

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val pieceSize = state.torrentProperties?.pieceSize
        Text(
            if (pieceSize != null) {
                stringResource(
                    CommonR.string.pieces_summary,
                    done,
                    pieces.size,
                    pieceSize.toHumanReadable(),
                )
            } else {
                stringResource(CommonR.string.pieces_map_description, done, pieces.size)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Legend(
            colors = colors,
            showPartial = grouped.piecesPerBlock > 1,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (grouped.piecesPerBlock > 1) {
            Text(
                stringResource(CommonR.string.pieces_grouped_note, grouped.piecesPerBlock),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        PiecesGrid(
            blocks = grouped.blocks,
            colors = colors,
            description = stringResource(CommonR.string.pieces_map_description, done, pieces.size),
        )
    }
}

@Composable
private fun PiecesGrid(
    blocks: List<BlockState>,
    colors: Map<BlockState, Color>,
    description: String,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = BLOCK_SIZE + BLOCK_GAP
        val columns = ((maxWidth + BLOCK_GAP) / stride).toInt().coerceAtLeast(1)
        val rows = (blocks.size + columns - 1) / columns
        Canvas(
            Modifier.fillMaxWidth().height(stride * rows - BLOCK_GAP).semantics {
                contentDescription = description
            },
        ) {
            val block = BLOCK_SIZE.toPx()
            val step = stride.toPx()
            val corner = CornerRadius(block / 5f)
            blocks.forEachIndexed { index, blockState ->
                drawRoundRect(
                    color = colors.getValue(blockState),
                    topLeft = Offset((index % columns) * step, (index / columns) * step),
                    size = Size(block, block),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(
    colors: Map<BlockState, Color>,
    showPartial: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(colors.getValue(BlockState.DONE), stringResource(CommonR.string.pieces_done))
        LegendItem(
            colors.getValue(BlockState.DOWNLOADING),
            stringResource(CommonR.string.pieces_downloading),
        )
        LegendItem(
            colors.getValue(BlockState.MISSING),
            stringResource(CommonR.string.pieces_missing),
        )
        if (showPartial) {
            LegendItem(
                colors.getValue(BlockState.PARTIAL),
                stringResource(CommonR.string.pieces_partial),
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(BLOCK_SIZE)) { drawRoundRect(color, cornerRadius = CornerRadius(2f)) }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
