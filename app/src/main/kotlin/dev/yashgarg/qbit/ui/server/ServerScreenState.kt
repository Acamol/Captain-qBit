package dev.yashgarg.qbit.ui.server

import qbittorrent.models.MainData
import qbittorrent.models.Torrent

data class ServerScreenState(
    val workspaceRevision: Long = 0,
    val dataLoading: Boolean = true,
    val data: MainData? = null,
    /** Whether the server has torrent queueing enabled. */
    val queueingEnabled: Boolean = false,
    val serverName: String? = null,
    val hasError: Boolean = false,
    val error: Throwable? = null,
    val selectedCategory: String? = null,
    val availableCategories: List<String> = emptyList(),
    val selectedFilter: StateFilter = StateFilter.ALL,
    val sortOption: SortOption = SortOption.NAME,
    val sortDirection: SortDirection = SortDirection.ASC,
    val searchQuery: String = "",
    val selectedTracker: String? = null,
    val availableTrackers: List<String> = emptyList(),
    val trackerCounts: Map<String, Int> = emptyMap(),
    val selectedTags: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val filterUntagged: Boolean = false,
) {
    internal fun includes(torrent: Torrent): Boolean =
        torrent.matchesSearch(searchQuery) &&
            torrent.matchesFilter(selectedFilter) &&
            torrent.matchesCategory(selectedCategory) &&
            torrent.matchesTracker(selectedTracker) &&
            torrent.matchesTags(selectedTags, filterUntagged)

    internal fun visibleTargets(requested: List<String>): List<String> {
        if (dataLoading || hasError || data == null) return emptyList()
        return requested.filter { hash -> data.torrents[hash]?.let(::includes) == true }
    }
}
