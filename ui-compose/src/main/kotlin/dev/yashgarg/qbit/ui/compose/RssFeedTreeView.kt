package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.BonsaiStyle
import cafe.adriel.bonsai.core.node.Branch
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Leaf
import cafe.adriel.bonsai.core.node.Node
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.core.tree.TreeScope
import dev.yashgarg.qbit.common.R as CommonR
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem

/**
 * Renders an RSS folder/feed tree. Hierarchy comes from indentation, the folder/feed icon, and name
 * weight. Tapping a feed leaf invokes [onFeedClick]; folders expand/collapse.
 *
 * Each row has a drag handle: dragging it over another row and releasing calls [onMove] with the
 * dragged item and its new path - dropping on most of a folder row (including its name) nests the
 * item inside that folder (expanded or collapsed); dropping on the thin strip at a row's very top
 * edge, or anywhere on a feed row, makes it a sibling at that row's own level instead. The list
 * auto-scrolls while dragged past the top/bottom of the viewport. Moving into a folder that doesn't
 * exist yet still needs the explicit "move to folder" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedTreeView(
    nodes: List<RssItem>,
    modifier: Modifier = Modifier,
    onFeedClick: (RssFeed) -> Unit,
    onFeedLongClick: (RssFeed) -> Unit = {},
    onFolderLongClick: (RssFolder) -> Unit = {},
    onMove: (item: RssItem, destPath: String) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // The controller is threaded down into the Tree{} content lambda below (so drag handles can
    // reach it), but it also needs that same Tree's node list once built - a genuine cycle since
    // Tree{}'s content runs synchronously inside the Tree() call. Broken by handing the
    // controller a lateinit reference, assigned right after Tree{} returns.
    val controller =
        remember(listState, scope, haptics) { RssDragController(listState, scope, haptics, onMove) }
    val tree = Tree<RssItem> { RssTree(nodes, controller) }
    controller.tree = tree

    Bonsai(
        tree,
        style = rssTreeStyle(controller),
        lazyListState = listState,
        modifier =
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).then(modifier),
        onClick = { node: Node<RssItem> ->
            if (node is BranchNode) {
                tree.clearSelection()
                tree.toggleExpansion(node)
            } else if (node.content is RssFeed) {
                onFeedClick(node.content as RssFeed)
            }
        },
        onLongClick = { node ->
            when (val content = node.content) {
                is RssFeed -> onFeedLongClick(content)
                is RssFolder -> onFolderLongClick(content)
            }
        },
    )
}

/**
 * Destination path for [moving], given the node it's dropped on. Dropping in a folder's nest zone
 * nests it inside that folder (expanded or not); dropping in its insert-above strip - or anywhere
 * on a feed row - makes it a sibling at the target's own level, which is how root becomes reachable
 * even when every visible row sits inside a folder.
 */
private fun destPathFor(target: Node<RssItem>, moving: RssItem, nestInside: Boolean): String? {
    val content = target.content
    if (content.path == moving.path) return null
    return if (target is BranchNode && nestInside) {
        "${content.path}\\${moving.name}"
    } else {
        val parent = content.path.substringBeforeLast('\\', missingDelimiterValue = "")
        if (parent.isEmpty()) moving.name else "$parent\\${moving.name}"
    }
}

/** True if moving [item] to [destPath] would nest a folder inside itself or its own subtree. */
private fun isInvalidMove(item: RssItem, destPath: String): Boolean {
    if (item !is RssFolder) return false
    return destPath == item.path || destPath.startsWith("${item.path}\\")
}

// Fraction of a row's height, from its top edge, reserved for "insert above this row" instead of
// "nest inside it". Small on purpose: most of the row - including its name - should mean nest.
private const val INSERT_ABOVE_ZONE = 0.2f

/**
 * Drives a drag-to-reorder gesture against [tree]'s current visible node list. Auto-scrolls
 * [listState] while the drag's estimated target index sits at the edge of what's visible.
 */
private class RssDragController(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val onMove: (RssItem, String) -> Unit,
) {
    lateinit var tree: Tree<RssItem>

    var draggedItem by mutableStateOf<RssItem?>(null)
        private set

    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    /** Path of the row currently under the drag, for the target-row highlight. */
    var targetPath by mutableStateOf<String?>(null)
        private set

    /** True while the target is in a folder's nest zone - i.e. "drop here nests inside it". */
    var isNestTarget by mutableStateOf(false)
        private set

    private var startIndex = -1
    private var lastTickIndex = -1
    private var autoScrollJob: Job? = null
    // Distance the list has been auto-scrolled since the drag started. dragOffsetY alone is the
    // finger's raw screen-space movement - once auto-scroll moves the content underneath a
    // (roughly) stationary finger, the target keeps drifting behind without this added back in.
    private var autoScrolledPx = 0f

    private fun fractionalTarget(): Float {
        val lastIndex = tree.nodes.lastIndex
        if (lastIndex < 0) return 0f
        val avgHeight =
            listState.layoutInfo.visibleItemsInfo.map { it.size }.average().takeIf { it > 0.0 }
                ?: 1.0
        // Allowed to land just past lastIndex (not clamped to it exactly) so the nest zone of
        // the very last row stays reachable; targetIndex() below still clamps the integer part
        // to a valid node index.
        return (startIndex + (dragOffsetY + autoScrolledPx) / avgHeight)
            .toFloat()
            .coerceIn(0f, lastIndex + 0.999f)
    }

    private fun targetIndex(): Int =
        floor(fractionalTarget()).toInt().coerceIn(0, tree.nodes.lastIndex.coerceAtLeast(0))

    /**
     * Whether the drag sits in the "nest" zone of [targetIndex]'s row - the bottom
     * [1 - INSERT_ABOVE_ZONE] of it, which deliberately includes the row's name (dead center is
     * squarely inside this zone) so aiming at a folder's name reliably nests into it. Only a thin
     * strip at the very top of a row means "insert above, at this row's own level" - enough to
     * still reach root by targeting a root folder's top edge.
     */
    private fun isNestZone(): Boolean {
        val f = fractionalTarget()
        return f - floor(f) >= INSERT_ABOVE_ZONE
    }

    fun start(item: RssItem) {
        draggedItem = item
        dragOffsetY = 0f
        autoScrolledPx = 0f
        startIndex = tree.nodes.indexOfFirst { it.content == item }
        lastTickIndex = startIndex
        autoScrollJob = scope.launch {
            while (isActive) {
                val info = listState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val target = targetIndex()
                when {
                    target <= first && first > 0 -> autoScrolledPx += listState.scrollBy(-14f)
                    target >= last && last < tree.nodes.lastIndex ->
                        autoScrolledPx += listState.scrollBy(14f)
                }
                delay(16)
            }
        }
    }

    fun drag(deltaY: Float) {
        dragOffsetY += deltaY
        val current = targetIndex()
        if (current != lastTickIndex) {
            lastTickIndex = current
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val target = tree.nodes.getOrNull(current)
        targetPath = target?.content?.path
        isNestTarget = target is BranchNode && isNestZone()
    }

    fun end() {
        autoScrollJob?.cancel()
        val item = draggedItem
        if (item != null) {
            val target = tree.nodes.getOrNull(targetIndex())
            val destPath = target?.let { destPathFor(it, item, isNestZone()) }
            if (destPath != null && destPath != item.path && !isInvalidMove(item, destPath)) {
                onMove(item, destPath)
            }
        }
        draggedItem = null
        targetPath = null
        isNestTarget = false
    }

    fun cancel() {
        autoScrollJob?.cancel()
        draggedItem = null
        targetPath = null
        isNestTarget = false
    }
}

@Composable
private fun rssTreeStyle(controller: RssDragController): BonsaiStyle<RssItem> {
    val iconTint = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    val draggedRowColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val nestTargetColor = MaterialTheme.colorScheme.primaryContainer
    val siblingTargetColor = MaterialTheme.colorScheme.secondaryContainer
    return BonsaiStyle(
        toggleIconSize = 20.dp,
        toggleIconColorFilter = iconTint,
        nodeIconSize = 24.dp,
        nodePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        nodeSpacing = 6.dp,
        nodeCollapsedIcon = { node ->
            rememberVectorPainter(
                if (node is BranchNode) Icons.Rounded.Folder else Icons.Filled.RssFeed
            )
        },
        nodeCollapsedIconColorFilter = iconTint,
        nodeNameStartPadding = 10.dp,
        // Whole-row treatment while dragging: lift the dragged row itself (tonal background,
        // drawn above its siblings via nodeOffsetY's z-index) and let it follow the finger; tint
        // whatever row it's currently over so the two drop outcomes stay visible before release -
        // a folder's lower half nests inside it (nestTargetColor), anything else makes the dragged
        // item a sibling at that row's level (siblingTargetColor).
        nodeBackgroundColor = { node ->
            when {
                controller.draggedItem == node.content -> draggedRowColor
                controller.draggedItem != null && controller.targetPath == node.content.path ->
                    if (controller.isNestTarget) nestTargetColor else siblingTargetColor
                else -> Color.Unspecified
            }
        },
        nodeOffsetY = { node ->
            if (controller.draggedItem == node.content) controller.dragOffsetY else 0f
        },
    )
}

@Composable
private fun TreeScope.RssTree(nodes: List<RssItem>, controller: RssDragController) {
    nodes.forEach { node -> RssNode(node, controller) }
}

@Composable
private fun TreeScope.RssNode(node: RssItem, controller: RssDragController) {
    when (node) {
        is RssFolder ->
            Branch(
                content = node,
                name = node.name,
                customName = { RssNodeName(it.content, controller) },
            ) {
                RssTree(node.children, controller)
            }
        is RssFeed ->
            Leaf(
                content = node,
                name = node.name,
                customName = { RssNodeName(it.content, controller) },
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssNodeName(node: RssItem, controller: RssDragController) {
    val unreadCount = (node as? RssFeed)?.articles?.count { !it.isRead } ?: 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = node.name,
            style =
                if (node is RssFeed) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.titleSmall,
            fontWeight = if (node is RssFeed) FontWeight.Medium else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (unreadCount > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = stringResource(CommonR.string.content_description_drag_to_move),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.pointerInput(node) {
                    detectDragGestures(
                        onDragStart = { controller.start(node) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            controller.drag(dragAmount.y)
                        },
                        onDragEnd = { controller.end() },
                        onDragCancel = { controller.cancel() },
                    )
                },
        )
    }
}
