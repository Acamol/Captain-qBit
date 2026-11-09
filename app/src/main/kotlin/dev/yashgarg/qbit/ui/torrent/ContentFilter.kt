package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.server.matchesWords

/**
 * The part of the content tree whose names match [query] (same AND-of-words rule as the torrent
 * list). A matching folder keeps everything under it; any other folder survives only with its
 * matching descendants. A blank query returns [nodes] unchanged.
 */
fun filterContentTree(nodes: List<ContentTreeItem>, query: String): List<ContentTreeItem> {
    if (query.isBlank()) return nodes
    return nodes.mapNotNull { node ->
        when {
            node.name.matchesWords(query) -> node
            node.item != null -> null
            else -> {
                val kept = filterContentTree(node.children.orEmpty(), query)
                if (kept.isEmpty()) null else node.copy(children = kept)
            }
        }
    }
}
