package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ConnectionType {
    HTTP,
    HTTPS
}
