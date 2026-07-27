package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem

/**
 * Renders an RSS folder/feed tree. Tapping a feed leaf invokes [onFeedClick]; folders
 * expand/collapse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedTreeView(
    nodes: List<RssItem>,
    modifier: Modifier = Modifier,
    onFeedClick: (RssFeed) -> Unit,
    onFeedLongClick: (RssFeed) -> Unit = {},
    onFolderLongClick: (RssFolder) -> Unit = {},
) {
    val tree = Tree<RssItem> { RssTree(nodes) }

    Bonsai(
        tree,
        style = rssTreeStyle(),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).then(modifier),
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

@Composable
private fun rssTreeStyle(): BonsaiStyle<RssItem> {
    val iconTint = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    return BonsaiStyle(
        toggleIconColorFilter = iconTint,
        nodeCollapsedIcon = { node ->
            rememberVectorPainter(
                if (node is BranchNode) Icons.Rounded.Folder else Icons.Filled.RssFeed
            )
        },
        nodeCollapsedIconColorFilter = iconTint,
        nodeNameStartPadding = 8.dp,
    )
}

@Composable
private fun TreeScope.RssTree(nodes: List<RssItem>) {
    nodes.forEach { node -> RssNode(node) }
}

@Composable
private fun TreeScope.RssNode(node: RssItem) {
    when (node) {
        is RssFolder ->
            Branch(content = node, name = node.name, customName = { RssNodeName(it.content) }) {
                RssTree(node.children)
            }
        is RssFeed ->
            Leaf(content = node, name = node.name, customName = { RssNodeName(it.content) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssNodeName(node: RssItem) {
    val unreadCount = (node as? RssFeed)?.articles?.count { !it.isRead } ?: 0
    Text(
        text = if (unreadCount > 0) "${node.name} ($unreadCount)" else node.name,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
