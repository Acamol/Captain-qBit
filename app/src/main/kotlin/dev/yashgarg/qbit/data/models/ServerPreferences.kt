package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ServerPreferences(
    val addTorrentAutoTmm: Boolean = false,
    val addTorrentPaused: Boolean = false,
    val sortOptionName: String = "NAME",
    val sortDirectionAsc: Boolean = true,
)
