package qbittorrent.models

import kotlinx.serialization.Serializable

/** A search plugin installed on the server. */
@Serializable
data class SearchPlugin(
    val name: String,
    val fullName: String = name,
    val version: String = "",
    val url: String = "",
    val enabled: Boolean = false,
    val supportedCategories: List<SearchCategory> = emptyList(),
)

@Serializable data class SearchCategory(val id: String, val name: String)
