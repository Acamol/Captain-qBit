package dev.yashgarg.qbit.ui.search

import qbittorrent.models.SearchPlugin
import qbittorrent.models.SearchResult

enum class SearchPhase {
    IDLE,
    RUNNING,
    FINISHED,
}

data class SearchState(
    val query: String = "",
    val plugins: List<SearchPlugin> = emptyList(),
    val pluginsLoaded: Boolean = false,
    /** Name of the single plugin to search with; null searches with every enabled plugin. */
    val selectedPlugin: String? = null,
    val category: String = CATEGORY_ALL,
    val sort: SearchSort = SearchSort.SEEDERS,
    val phase: SearchPhase = SearchPhase.IDLE,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
) {
    val enabledPlugins: List<SearchPlugin>
        get() = plugins.filter { it.enabled }

    /**
     * Categories every plugin in scope supports, so a picked category can't silently match none.
     */
    val categories: List<Pair<String, String>>
        get() {
            val inScope =
                if (selectedPlugin != null) plugins.filter { it.name == selectedPlugin }
                else enabledPlugins
            return inScope
                .map { plugin -> plugin.supportedCategories.map { it.id to it.name }.toSet() }
                .reduceOrNull { acc, set -> acc intersect set }
                .orEmpty()
                .filter { it.first != CATEGORY_ALL }
        }

    val sortedResults: List<SearchResult>
        get() = results.sortedBy(sort)

    companion object {
        const val CATEGORY_ALL = "all"
    }
}
