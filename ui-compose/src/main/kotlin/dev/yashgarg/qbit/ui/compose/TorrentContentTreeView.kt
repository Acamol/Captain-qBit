package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import dev.yashgarg.qbit.data.models.ContentTreeItem

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
    filterKey: String = "",
    onFileClick: ((ContentTreeItem) -> Unit)? = null,
) {
    val tree = Tree<ContentTreeItem> { ContentTree(nodes, onToggleDownload) }
    // A non-blank [filterKey] opens every folder so the filtered matches are visible.
    LaunchedEffect(filterKey) { if (filterKey.isNotBlank()) tree.expandAll() }

    Bonsai(
        tree,
        style = torrentContentStyle(showIcons = onToggleDownload == null),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).then(modifier),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        onClick = { node: Node<ContentTreeItem> ->
            if (node is BranchNode) {
                tree.clearSelection()
                tree.toggleExpansion(node)
            } else if (onFileClick != null) {
                onFileClick(node.content)
            } else if (onToggleDownload != null) {
                onToggleDownload(node.content)
            }
        },
        onLongClick = { node -> onNodeLongClick(node.content) },
    )
}

/** True when this file — or every file under this folder — is set to not download. */
fun ContentTreeItem.isSkipped(): Boolean {
    val node = whole()
    return node.item?.let { it.priority == FILE_PRIORITY_SKIP }
        ?: (!node.children.isNullOrEmpty() && node.children!!.all { it.isSkipped() })
}

@Composable
private fun torrentContentStyle(showIcons: Boolean): BonsaiStyle<ContentTreeItem> {
    val iconTint = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    return BonsaiStyle(
        toggleIconColorFilter = iconTint,
        toggleIconExpandedContentDescription =
            stringResource(CommonR.string.content_description_collapse_folder),
        toggleIconCollapsedContentDescription =
            stringResource(CommonR.string.content_description_expand_folder),
        nodeCollapsedIcon = { node ->
            if (showIcons)
                rememberVectorPainter(
                    if (node is BranchNode) Icons.Rounded.Folder else Icons.Default.FileCopy,
                )
            else null
        },
        nodeCollapsedIconColorFilter = iconTint,
        nodeNameStartPadding = 8.dp,
        nodePadding = PaddingValues(horizontal = 4.dp),
        nodeShape = MaterialTheme.shapes.small,
        nodeBackgroundColor = { Color.Unspecified },
        nodeSpacing = 0.dp,
    )
}

@Composable
private fun TreeScope.ContentTree(
    nodes: List<ContentTreeItem>,
    onToggleDownload: ((ContentTreeItem) -> Unit)?,
) {
    // Keyed so each folder keeps its expansion when a filter changes which nodes are listed.
    nodes.forEach { node -> key(node.id) { ContentNode(node, onToggleDownload) } }
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
    val priority =
        when (item.item?.priority) {
            FILE_PRIORITY_HIGH -> stringResource(CommonR.string.file_priority_high)
            FILE_PRIORITY_MAXIMAL -> stringResource(CommonR.string.file_priority_maximal)
            else -> null
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onToggleDownload != null) {
            TriStateCheckbox(
                state = item.downloadState(),
                onClick = { onToggleDownload(item) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = item.name },
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (skipped) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (skipped) TextDecoration.LineThrough else null,
            )
            if (priority != null) {
                Text(
                    priority,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.size > 0) {
            val progress = (item.progress.toDouble() / item.size).toFloat().coerceIn(0f, 1f)
            Text(
                stringResource(CommonR.string.torrent_progress_percent, (progress * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ContentTreeItem.downloadState(): ToggleableState {
    val priorities = allPriorities()
    val skipped = priorities.count { it == FILE_PRIORITY_SKIP }
    return when {
        skipped == 0 -> ToggleableState.On
        skipped == priorities.size -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}

private fun ContentTreeItem.allPriorities(): List<Int> {
    val node = whole()
    return node.item?.let { listOf(it.priority) }
        ?: node.children.orEmpty().flatMap { it.allPriorities() }
}
