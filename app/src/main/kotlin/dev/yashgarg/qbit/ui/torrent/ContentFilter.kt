package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.server.matchesAll
import dev.yashgarg.qbit.ui.server.searchWords

/**
 * The part of the content tree whose names match [query] (same AND-of-words rule as the torrent
 * list). A matching folder keeps everything under it; any other folder survives only with its
 * matching descendants, and remembers its complete contents in [ContentTreeItem.complete]. A blank
 * query returns [nodes] unchanged.
 */
fun filterContentTree(nodes: List<ContentTreeItem>, query: String): List<ContentTreeItem> {
    val words = searchWords(query)
    return if (words.isEmpty()) nodes else filterNodes(nodes, words)
}

private fun filterNodes(nodes: List<ContentTreeItem>, words: List<String>): List<ContentTreeItem> =
    nodes.mapNotNull { node ->
        when {
            node.name.matchesAll(words) -> node
            node.item != null -> null
            else -> {
                val kept = filterNodes(node.children.orEmpty(), words)
                if (kept.isEmpty()) null else node.copy(children = kept, complete = node.whole())
            }
        }
    }
