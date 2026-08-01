package qbittorrent.models

import kotlinx.serialization.Serializable

/**
 * An RSS auto-downloading rule. Mirrors the subset of qBittorrent's rule definition exposed by its
 * own rule editor: match text (raw, same semantics as the desktop client - not parsed client-side),
 * an optional paused/started override, and where matched torrents are filed.
 */
@Serializable
data class RssRule(
    val enabled: Boolean = true,
    val mustContain: String = "",
    val mustNotContain: String = "",
    val useRegex: Boolean = false,
    val episodeFilter: String = "",
    val smartFilter: Boolean = false,
    val ignoreDays: Int = 0,
    val addPaused: Boolean? = null,
    val assignedCategory: String = "",
    val savePath: String = "",
    val torrentContentLayout: String = "Original",
    val affectedFeeds: List<String> = emptyList(),
)
