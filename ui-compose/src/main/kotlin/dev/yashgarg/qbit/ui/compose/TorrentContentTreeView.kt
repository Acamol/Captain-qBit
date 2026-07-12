package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.BonsaiStyle
import cafe.adriel.bonsai.core.node.Branch
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Leaf
import cafe.adriel.bonsai.core.node.Node
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.core.tree.TreeScope
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.theme.SpaceGrotesk

// qBittorrent file priorities.
const val FILE_PRIORITY_SKIP = 0
const val FILE_PRIORITY_NORMAL = 1
const val FILE_PRIORITY_HIGH = 6
const val FILE_PRIORITY_MAXIMAL = 7

@Composable
fun TorrentContentTreeView(
    modifier: Modifier = Modifier,
    nodes: List<ContentTreeItem>,
    onNodeLongClick: (ContentTreeItem) -> Unit = {},
    onToggleDownload: ((ContentTreeItem) -> Unit)? = null,
) {
    val tree = Tree<ContentTreeItem> { ContentTree(nodes, onToggleDownload) }

    Bonsai(
        tree,
        style = torrentContentStyle(),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).then(modifier),
        // Folders keep the default expand/collapse tap; tapping a file flips its download
        // checkbox (when the caller provided a toggle handler).
        onClick = { node: Node<ContentTreeItem> ->
            if (node is BranchNode || onToggleDownload == null) {
                tree.clearSelection()
                tree.toggleExpansion(node)
            } else {
                onToggleDownload(node.content)
            }
        },
        onLongClick = { node -> onNodeLongClick(node.content) },
    )
}

/** True when this file — or every file under this folder — is set to not download. */
fun ContentTreeItem.isSkipped(): Boolean =
    item?.let { it.priority == FILE_PRIORITY_SKIP }
        ?: (!children.isNullOrEmpty() && children!!.all { it.isSkipped() })

@Composable
private fun torrentContentStyle(): BonsaiStyle<ContentTreeItem> {
    val iconTint = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    return BonsaiStyle(
        toggleIconColorFilter = iconTint,
        nodeCollapsedIcon = { node ->
            rememberVectorPainter(
                if (node is BranchNode) Icons.Rounded.Folder else Icons.Default.FileCopy
            )
        },
        nodeCollapsedIconColorFilter = iconTint,
        nodeNameStartPadding = 8.dp,
    )
}

@Composable
private fun TreeScope.ContentTree(
    nodes: List<ContentTreeItem>,
    onToggleDownload: ((ContentTreeItem) -> Unit)?,
) {
    nodes.forEach { node -> ContentNode(node, onToggleDownload) }
}

@Composable
private fun TreeScope.ContentNode(
    node: ContentTreeItem,
    onToggleDownload: ((ContentTreeItem) -> Unit)?,
) {
    // If [node.item] is null, therefore it is a directory
    if (node.item == null) {
        Branch(
            content = node,
            name = node.name,
            customName = { NodeName(it.content, onToggleDownload) },
        ) {
            node.children?.let { ContentTree(it, onToggleDownload) }
        }
    } else {
        Leaf(
            content = node,
            name = node.name,
            customName = { NodeName(it.content, onToggleDownload) },
        )
    }
}

// A download checkbox (tri-state for folders) plus the name. Skipped content is dimmed and struck
// through; raised priorities get a small marker, matching the official client's priority column.
@Composable
private fun NodeName(item: ContentTreeItem, onToggleDownload: ((ContentTreeItem) -> Unit)?) {
    val skipped = item.isSkipped()
    val suffix =
        when (item.item?.priority) {
            FILE_PRIORITY_HIGH -> "  ↑"
            FILE_PRIORITY_MAXIMAL -> "  ↑↑"
            else -> ""
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onToggleDownload != null) {
            TriStateCheckbox(
                state = item.downloadState(),
                onClick = { onToggleDownload(item) },
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = item.name + suffix,
            fontSize = 16.sp,
            fontFamily = SpaceGrotesk,
            color =
                if (skipped) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (skipped) TextDecoration.LineThrough else null,
        )
    }
}

private fun ContentTreeItem.downloadState(): ToggleableState {
    val priorities =
        item?.let { listOf(it.priority) }
            ?: children.orEmpty().flatMap { child -> child.allPriorities() }
    val skipped = priorities.count { it == FILE_PRIORITY_SKIP }
    return when {
        skipped == 0 -> ToggleableState.On
        skipped == priorities.size -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}

private fun ContentTreeItem.allPriorities(): List<Int> =
    item?.let { listOf(it.priority) } ?: children.orEmpty().flatMap { it.allPriorities() }
