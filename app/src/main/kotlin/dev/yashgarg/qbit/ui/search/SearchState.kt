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
            val offered = inScope.map { plugin ->
                plugin.supportedCategories.filter { it.id != CATEGORY_ALL }
            }
            // Plugins may label the same id differently, so match on the id and show the first
            // name.
            val shared =
                offered
                    .map { list -> list.map { it.id }.toSet() }
                    .reduceOrNull { a, b -> a intersect b }
            return offered
                .firstOrNull()
                .orEmpty()
                .filter { it.id in shared.orEmpty() }
                .map { it.id to it.name }
        }

    companion object {
        const val CATEGORY_ALL = "all"
    }
}
