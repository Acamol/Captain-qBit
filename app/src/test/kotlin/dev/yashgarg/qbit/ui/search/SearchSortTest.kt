package dev.yashgarg.qbit.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.SearchResult

class SearchSortTest {
    private fun result(name: String, seeders: Int = -1, size: Long = -1) =
        SearchResult(
            fileName = name,
            fileUrl = "magnet:?$name",
            nbSeeders = seeders,
            fileSize = size,
        )

    @Test
    fun `seeders sort puts the most seeded first and unknown counts last`() {
        val sorted =
            listOf(result("a", 3), result("b", -1), result("c", 40)).sortedBy(SearchSort.SEEDERS)

        assertEquals(listOf("c", "a", "b"), sorted.map { it.fileName })
    }

    @Test
    fun `size sort puts the largest first and unknown sizes last`() {
        val sorted =
            listOf(result("a", size = 10), result("b"), result("c", size = 99))
                .sortedBy(SearchSort.SIZE)

        assertEquals(listOf("c", "a", "b"), sorted.map { it.fileName })
    }

    @Test
    fun `name sort is alphabetical regardless of case`() {
        val sorted = listOf(result("beta"), result("Alpha")).sortedBy(SearchSort.NAME)

        assertEquals(listOf("Alpha", "beta"), sorted.map { it.fileName })
    }
}
