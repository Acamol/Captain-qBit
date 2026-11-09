package dev.yashgarg.qbit.ui.torrent

import dev.yashgarg.qbit.data.models.ContentTreeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import qbittorrent.models.TorrentFile

class ContentFilterTest {
    private fun file(index: Int, name: String) =
        ContentTreeItem(
            id = index,
            name = name,
            path = name,
            item =
                TorrentFile(
                    index = index,
                    name = name,
                    size = 1,
                    progress = 0f,
                    priority = 1,
                    pieceRange = listOf(0, 0),
                    availability = 1f,
                ),
            size = 1,
            progress = 0,
        )

    private fun folder(id: Int, name: String, vararg children: ContentTreeItem) =
        ContentTreeItem(
            id = id,
            name = name,
            path = name,
            children = children.toList(),
            size = 1,
            progress = 0,
        )

    private val tree =
        listOf(
            folder(
                100,
                "Season 1",
                file(0, "Show S01E01.mkv"),
                file(1, "Show S01E02.mkv"),
                folder(101, "Extras", file(2, "Bloopers.mkv")),
            ),
            file(3, "readme.txt"),
        )

    private fun names(nodes: List<ContentTreeItem>): List<String> = nodes.flatMap {
        listOf(it.name) + names(it.children.orEmpty())
    }

    @Test
    fun `a blank query returns the tree untouched`() {
        assertSame(tree, filterContentTree(tree, "  "))
    }

    @Test
    fun `a matching file keeps only its parent folders`() {
        val filtered = filterContentTree(tree, "e02")

        assertEquals(listOf("Season 1", "Show S01E02.mkv"), names(filtered))
    }

    @Test
    fun `a matching folder keeps everything beneath it`() {
        val filtered = filterContentTree(tree, "extras")

        assertEquals(listOf("Season 1", "Extras", "Bloopers.mkv"), names(filtered))
    }

    @Test
    fun `all words must match in any order and ignoring case`() {
        assertEquals(listOf("readme.txt"), names(filterContentTree(tree, "TXT readme")))
        assertEquals(emptyList<String>(), names(filterContentTree(tree, "readme mkv")))
    }

    @Test
    fun `a query that matches nothing returns an empty tree`() {
        assertEquals(emptyList<ContentTreeItem>(), filterContentTree(tree, "zzz"))
    }
}
