package dev.yashgarg.qbit.ui.search

import qbittorrent.models.SearchResult

enum class SearchSort {
    SEEDERS,
    SIZE,
    NAME,
}

/**
 * Orders [this] by [sort]: seeders and size descending, name ascending. Hits whose seeder count or
 * size the plugin couldn't report (-1) always go last.
 */
fun List<SearchResult>.sortedBy(sort: SearchSort): List<SearchResult> =
    when (sort) {
        SearchSort.SEEDERS ->
            sortedWith(
                compareByDescending<SearchResult> { it.nbSeeders >= 0 }
                    .thenByDescending { it.nbSeeders },
            )
        SearchSort.SIZE ->
            sortedWith(
                compareByDescending<SearchResult> { it.fileSize >= 0 }
                    .thenByDescending { it.fileSize },
            )
        SearchSort.NAME -> sortedBy { it.fileName.lowercase() }
    }
