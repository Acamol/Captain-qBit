package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.theme.AppTypography
import dev.yashgarg.qbit.ui.compose.theme.bodyMediumPrimary
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.utils.toHumanReadable
import java.text.NumberFormat
import qbittorrent.models.PieceState

// Beyond this many blocks the grid gets too tall to read and slow to draw, so pieces are grouped.
private const val MAX_BLOCKS = 4096
// A block at a file boundary of a many-small-files torrent can span hundreds of files.
private const val MAX_LISTED_FILES = 20
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
    var selectedBlock by rememberSaveable { mutableStateOf<Int?>(null) }
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
            )
        }
        Text(
            stringResource(CommonR.string.pieces_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        PiecesGrid(
            blocks = grouped.blocks,
            colors = colors,
            description = stringResource(CommonR.string.pieces_map_description, done, pieces.size),
            onBlockTap = { selectedBlock = it },
        )
    }

    // The map can shrink under an open dialog (e.g. a recheck), so drop a block that's gone.
    selectedBlock
        ?.takeIf { it < grouped.blocks.size }
        ?.let { block ->
            PieceDetailsDialog(
                pieceRange = grouped.piecesIn(block, pieces.size),
                state = state,
                onDismiss = { selectedBlock = null },
            )
        }
}

@Composable
private fun PieceDetailsDialog(
    pieceRange: IntRange,
    state: TorrentDetailsState,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val total = number.format(state.pieceStates.size)
    val states = state.pieceStates.slice(pieceRange)
    val files = remember(pieceRange, state.files) { filesIn(pieceRange, state.files) }
    val title =
        if (pieceRange.first == pieceRange.last) {
            stringResource(
                CommonR.string.piece_details_title,
                number.format(pieceRange.first + 1),
                total,
            )
        } else {
            stringResource(
                CommonR.string.piece_details_group_title,
                number.format(pieceRange.first + 1),
                number.format(pieceRange.last + 1),
                total,
            )
        }

    AlertDialog(
        tonalElevation = 5.dp,
        onDismissRequest = onDismiss,
        title = { Text(title, style = AppTypography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (states.size == 1) {
                    DetailRow(
                        stringResource(CommonR.string.piece_details_status),
                        stringResource(stateLabel(states.single())),
                    )
                } else {
                    PieceState.entries.reversed().forEach { pieceState ->
                        DetailRow(
                            stringResource(stateLabel(pieceState)),
                            number.format(states.count { it == pieceState }),
                        )
                    }
                }
                val props = state.torrentProperties
                if (props != null && props.pieceSize > 0) {
                    val bytes = byteRange(pieceRange, props.pieceSize, props.totalSize)
                    if (!bytes.isEmpty()) {
                        DetailRow(
                            stringResource(CommonR.string.piece_details_size),
                            (bytes.last - bytes.first + 1).toHumanReadable(),
                        )
                        DetailRow(
                            stringResource(CommonR.string.piece_details_bytes),
                            stringResource(
                                CommonR.string.piece_details_bytes_value,
                                number.format(bytes.first),
                                number.format(bytes.last),
                            ),
                        )
                    }
                }
                Text(
                    stringResource(CommonR.string.tab_files),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (files.isEmpty()) {
                    Text(
                        stringResource(CommonR.string.piece_details_no_files),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                files.take(MAX_LISTED_FILES).forEach { file ->
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (files.size > MAX_LISTED_FILES) {
                    val more = files.size - MAX_LISTED_FILES
                    Text(
                        pluralStringResource(CommonR.plurals.piece_details_more_files, more, more),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.close_action), style = bodyMediumPrimary)
            }
        },
    )
}

private fun stateLabel(state: PieceState): Int =
    when (state) {
        PieceState.DONE -> CommonR.string.pieces_done
        PieceState.DOWNLOADING -> CommonR.string.pieces_downloading
        PieceState.MISSING -> CommonR.string.pieces_missing
    }

@Composable
private fun PiecesGrid(
    blocks: List<BlockState>,
    colors: Map<BlockState, Color>,
    description: String,
    onBlockTap: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = BLOCK_SIZE + BLOCK_GAP
        val columns = ((maxWidth + BLOCK_GAP) / stride).toInt().coerceAtLeast(1)
        val rows = (blocks.size + columns - 1) / columns
        Canvas(
            Modifier.fillMaxWidth()
                .height(stride * rows - BLOCK_GAP)
                .semantics { contentDescription = description }
                .pointerInput(columns, blocks.size) {
                    detectTapGestures { offset ->
                        val step = stride.toPx()
                        val column = (offset.x / step).toInt()
                        val index = (offset.y / step).toInt() * columns + column
                        if (column < columns && index in blocks.indices) onBlockTap(index)
                    }
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
