package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
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

/** All torrent file indices under this node (itself, for a file). */
fun ContentTreeItem.allFileIndices(): List<Int> =
    item?.let { listOf(it.index) } ?: children.orEmpty().flatMap { it.allFileIndices() }

/**
 * The content tree with a checkbox per node, for choosing which files to download when adding a
 * torrent. [deselected] holds the file indices the user excluded; folder checkboxes reflect (and
 * toggle) everything beneath them.
 */
@Composable
fun TorrentContentSelectionView(
    nodes: List<ContentTreeItem>,
    deselected: Set<Int>,
    onToggle: (ContentTreeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tree = Tree<ContentTreeItem> { SelectionTree(nodes, deselected, onToggle) }

    Bonsai(
        tree,
        style = selectionStyle(),
        modifier = Modifier.fillMaxSize().then(modifier),
        // Folders expand/collapse on tap; a file tap toggles its checkbox.
        onClick = { node: Node<ContentTreeItem> ->
            if (node is BranchNode) {
                tree.clearSelection()
                tree.toggleExpansion(node)
            } else {
                onToggle(node.content)
            }
        },
        onLongClick = null,
    )
}

@Composable
private fun selectionStyle(): BonsaiStyle<ContentTreeItem> =
    BonsaiStyle(nodeNameStartPadding = 4.dp)

@Composable
private fun TreeScope.SelectionTree(
    nodes: List<ContentTreeItem>,
    deselected: Set<Int>,
    onToggle: (ContentTreeItem) -> Unit,
) {
    nodes.forEach { node -> SelectionNode(node, deselected, onToggle) }
}

@Composable
private fun TreeScope.SelectionNode(
    node: ContentTreeItem,
    deselected: Set<Int>,
    onToggle: (ContentTreeItem) -> Unit,
) {
    if (node.item == null) {
        Branch(
            content = node,
            name = node.name,
            customName = { NodeRow(it.content, deselected, onToggle) },
        ) {
            node.children?.let { SelectionTree(it, deselected, onToggle) }
        }
    } else {
        Leaf(
            content = node,
            name = node.name,
            customName = { NodeRow(it.content, deselected, onToggle) },
        )
    }
}

@Composable
private fun NodeRow(
    item: ContentTreeItem,
    deselected: Set<Int>,
    onToggle: (ContentTreeItem) -> Unit,
) {
    val indices = item.allFileIndices()
    val excluded = indices.count { it in deselected }
    val state =
        when {
            excluded == 0 -> ToggleableState.On
            excluded == indices.size -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TriStateCheckbox(
            state = state,
            onClick = { onToggle(item) },
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = item.name,
            fontSize = 15.sp,
            fontFamily = SpaceGrotesk,
            color =
                if (state == ToggleableState.Off)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}
