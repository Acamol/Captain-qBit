package qbittorrent.models

import kotlinx.serialization.Serializable

/** A single hit returned by a search job. [nbSeeders] and [nbLeechers] are -1 when unknown. */
@Serializable
data class SearchResult(
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long = -1,
    val nbSeeders: Int = -1,
    val nbLeechers: Int = -1,
    val siteUrl: String = "",
    val descrLink: String = "",
    val engineName: String? = null,
    val pubDate: Long? = null,
)

/** One page of results; [status] is "Running" or "Stopped" and [total] counts all hits so far. */
@Serializable
data class SearchResultsPage(
    val results: List<SearchResult> = emptyList(),
    val status: String = "",
    val total: Int = 0,
) {
    val isRunning: Boolean
        get() = status.equals("Running", ignoreCase = true)
}
