package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ServerPreferences(
    val addTorrentAutoTmm: Boolean = false,
    val addTorrentPaused: Boolean = false,
    val addTorrentCategory: String = "",
    val sortOptionName: String = "NAME",
    val sortDirectionAsc: Boolean = true,
    val filterStateName: String = "ALL",
    val filterCategory: String? = null,
    val filterTracker: String? = null,
    val filterTags: Set<String> = emptySet(),
    val filterUntagged: Boolean = false,
)
