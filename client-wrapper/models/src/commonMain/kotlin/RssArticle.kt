package qbittorrent.models

import kotlinx.serialization.Serializable

/** A single entry in an RSS feed. */
@Serializable
data class RssArticle(
    val id: String,
    val date: String,
    val title: String,
    val link: String = "",
    val description: String? = null,
    val author: String? = null,
    val category: String? = null,
    val isRead: Boolean = false,
    val torrentURL: String? = null,
)
