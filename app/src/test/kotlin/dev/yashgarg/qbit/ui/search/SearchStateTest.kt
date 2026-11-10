package dev.yashgarg.qbit.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.SearchCategory
import qbittorrent.models.SearchPlugin

class SearchStateTest {
    private fun plugin(name: String, vararg categories: Pair<String, String>) =
        SearchPlugin(
            name = name,
            fullName = name,
            version = "1",
            url = "https://$name.example",
            enabled = true,
            supportedCategories = categories.map { SearchCategory(it.first, it.second) },
        )

    @Test
    fun `categories shared by id are offered even when plugins name them differently`() {
        val state =
            SearchState(
                plugins =
                    listOf(
                        plugin("a", "movies" to "Movies", "music" to "Music"),
                        plugin("b", "movies" to "Video/Movies", "books" to "Books"),
                    ),
            )

        assertEquals(listOf("movies" to "Movies"), state.categories)
    }

    @Test
    fun `the all category is never listed`() {
        val state = SearchState(plugins = listOf(plugin("a", "all" to "All", "tv" to "TV")))

        assertEquals(listOf("tv" to "TV"), state.categories)
    }

    @Test
    fun `a selected plugin narrows the categories to its own`() {
        val state =
            SearchState(
                plugins = listOf(plugin("a", "tv" to "TV"), plugin("b", "music" to "Music")),
                selectedPlugin = "b",
            )

        assertEquals(listOf("music" to "Music"), state.categories)
    }
}
