package dev.yashgarg.qbit.ui.server

import qbittorrent.models.MainData

data class ServerScreenState(
    val dataLoading: Boolean = true,
    val data: MainData? = null,
    val speedLimitMode: Int = 0,
    /** Global speed limits in bytes/s; 0 = unlimited. */
    val globalDownloadLimit: Int = 0,
    val globalUploadLimit: Int = 0,
    /** Alternate speed limits in bytes/s; 0 = unlimited. */
    val altDownloadLimit: Int = 0,
    val altUploadLimit: Int = 0,
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
    val selectedTags: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val filterUntagged: Boolean = false,
)
