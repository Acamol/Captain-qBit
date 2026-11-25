package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.isolateLtr
import qbittorrent.models.Torrent

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TorrentRow(
    torrent: Torrent,
    categoryColors: Map<String, Int>,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val visuals = rowVisuals(torrent)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val downloading =
        torrent.state == Torrent.State.DOWNLOADING || torrent.state == Torrent.State.FORCED_DL

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = if (compact) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else if (downloading) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(
            Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onClickLabel =
                        stringResource(
                            if (!selectionActive) CommonR.string.open_torrent_details_action
                            else if (selected) CommonR.string.deselect_torrent_action
                            else CommonR.string.select_torrent_action,
                        ),
                    onLongClickLabel =
                        stringResource(
                            if (selected) CommonR.string.deselect_torrent_action
                            else CommonR.string.select_torrent_action,
                        ),
                    role = Role.Button,
                )
                .semantics { this.selected = selected }
                .padding(if (compact) 12.dp else 16.dp),
        ) {
            Column {
                Text(
                    torrent.name,
                    style =
                        if (compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.fillMaxWidth()
                            .then(
                                if (selectionActive && selected) Modifier.padding(end = 28.dp)
                                else Modifier,
                            ),
                )
                if (!compact) {
                    Text(
                        visuals.peersText,
                        style = MaterialTheme.typography.labelMedium,
                        color = visuals.peersColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                val hasCategory = torrent.category.isNotBlank()
                if (compact || hasCategory || torrent.tags.isNotEmpty()) {
                    FlowRow(
                        modifier =
                            Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (compact) {
                            Text(
                                visuals.peersText,
                                style = MaterialTheme.typography.labelMedium,
                                color = visuals.peersColor,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                        if (hasCategory) {
                            val userColor = categoryColors[torrent.category]?.let { Color(it) }
                            if (userColor != null) {
                                MiniChip(torrent.category, userColor, contrastOn(userColor), false)
                            } else {
                                MiniChip(
                                    torrent.category,
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                    false,
                                )
                            }
                        }
                        torrent.tags.forEach { tag ->
                            MiniChip("#$tag".isolateLtr(), null, muted, false)
                        }
                    }
                }

                TorrentTransferSummary(
                    torrent = torrent,
                    progressColor = visuals.progressColor,
                    showEta = visuals.showEta,
                    compact = compact,
                )
            }

            if (selectionActive && selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, background: Color?, foreground: Color, outlined: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = background ?: Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(
            text,
            color = foreground,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private data class RowVisuals(
    val peersText: String,
    val peersColor: Color,
    val showEta: Boolean,
    val progressColor: Color,
)

@Composable
private fun rowVisuals(torrent: Torrent): RowVisuals {
    val grey = colorResource(R.color.grey)
    val green = colorResource(R.color.green)
    val red = colorResource(R.color.red)
    val yellow = colorResource(R.color.yellow)
    val primary = MaterialTheme.colorScheme.primary
    val label = stringResource(torrent.state.labelRes())

    return when (torrent.state) {
        Torrent.State.PAUSED_DL,
        Torrent.State.STOPPED_DL,
        Torrent.State.PAUSED_UP,
        Torrent.State.STOPPED_UP -> RowVisuals(label, grey, false, grey)
        Torrent.State.UPLOADING,
        Torrent.State.FORCED_UP,
        Torrent.State.STALLED_UP -> RowVisuals(label, green, false, green)
        Torrent.State.DOWNLOADING,
        Torrent.State.FORCED_DL ->
            RowVisuals(
                stringResource(
                    CommonR.string.seed_status,
                    torrent.connectedSeeds,
                    torrent.seedsInSwarm,
                ),
                primary,
                true,
                primary,
            )
        Torrent.State.STALLED_DL -> RowVisuals(label, red, false, red)
        Torrent.State.ERROR,
        Torrent.State.MISSING_FILES ->
            RowVisuals(
                label,
                red,
                false,
                red,
            )
        Torrent.State.META_DL,
        Torrent.State.FORCED_META_DL,
        Torrent.State.ALLOCATING,
        Torrent.State.MOVING,
        Torrent.State.CHECKING_DL,
        Torrent.State.CHECKING_UP,
        Torrent.State.CHECKING_RESUME_DATA,
        Torrent.State.QUEUED_DL,
        Torrent.State.QUEUED_UP,
        Torrent.State.UNKNOWN -> RowVisuals(label, yellow, false, yellow)
    }
}

private fun contrastOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

/**
 * [TorrentRow] with a swipe-left-to-reveal action drawer (Pause/Resume + Delete) that stays open
 * until an action is tapped, the card is swiped back, or (tapping the card) it snaps shut. Disabled
 * while multi-select is active so the swipe doesn't fight the selection gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableTorrentRow(
    torrent: Torrent,
    compact: Boolean = false,
    categoryColors: Map<String, Int>,
    selected: Boolean,
    selectionActive: Boolean,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val actionWidth = 76.dp
    val density = LocalDensity.current
    val paused = torrent.isPaused()
    val scrollState = rememberScrollState()
    val revealPx = with(density) { (actionWidth * 2).roundToPx() }

    // Reveal is a real horizontal scroll: the card sits at full item width with the actions laid
    // out
    // just off its right edge, scrolled into view. Using the framework's scroll (not a custom
    // draggable) is what makes it reliable — combinedClickable natively yields to a parent scroll
    // container, so tap/long-press and the swipe no longer fight each other.
    //
    // Whether this row is open is hoisted to the caller ([revealed]) so only one row is open at a
    // time and Back can close it. After a user scroll settles, report which anchor it landed on and
    // snap to it; [revealed] changing (another row opened, Back, an action) animates it there.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            val shouldOpen = scrollState.value > revealPx / 2
            val target = if (shouldOpen) revealPx else 0
            if (scrollState.value != target) scrollState.animateScrollTo(target)
            onRevealChange(shouldOpen)
        }
    }
    LaunchedEffect(revealed) {
        val target = if (revealed) revealPx else 0
        if (!scrollState.isScrollInProgress && scrollState.value != target) {
            scrollState.animateScrollTo(target)
        }
    }

    BoxWithConstraints {
        val cardWidth = maxWidth
        Layout(
            modifier = Modifier.horizontalScroll(scrollState, enabled = !selectionActive),
            content = {
                Box(Modifier.width(cardWidth)) {
                    TorrentRow(
                        torrent = torrent,
                        compact = compact,
                        categoryColors = categoryColors,
                        selected = selected,
                        selectionActive = selectionActive,
                        onClick = {
                            if (scrollState.value != 0) onRevealChange(false) else onClick()
                        },
                        onLongClick = onLongClick,
                    )
                }
                RevealAction(
                    icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label =
                        stringResource(
                            if (paused) CommonR.string.resume else CommonR.string.pause_action,
                        ),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    width = actionWidth,
                    onClick = {
                        onPauseResume()
                        onRevealChange(false)
                    },
                )
                RevealAction(
                    icon = Icons.Filled.Delete,
                    label = stringResource(CommonR.string.delete),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    width = actionWidth,
                    onClick = {
                        onDelete()
                        onRevealChange(false)
                    },
                )
            },
        ) { measurables, constraints ->
            // Measure wrapped content before sizing the action rail; intrinsic height can clip it.
            val card = measurables.first().measure(constraints.copy(minHeight = 0))
            val actions =
                measurables.drop(1).map {
                    it.measure(constraints.copy(minHeight = card.height, maxHeight = card.height))
                }
            layout(card.width + actions.sumOf { it.width }, card.height) {
                card.placeRelative(0, 0)
                var x = card.width
                actions.forEach { action ->
                    action.placeRelative(x, 0)
                    x += action.width
                }
            }
        }
    }
}

@Composable
private fun RevealAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxHeight()
                .width(width)
                .background(container)
                .clickable(onClick = onClick, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(label, color = content, fontSize = 11.sp)
    }
}

internal fun Torrent.isPaused(): Boolean =
    state == Torrent.State.PAUSED_DL ||
        state == Torrent.State.PAUSED_UP ||
        state == Torrent.State.STOPPED_DL ||
        state == Torrent.State.STOPPED_UP
