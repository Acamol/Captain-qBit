package dev.yashgarg.qbit.ui.version

data class VersionState(
    val loading: Boolean = true,
    val serverVersion: String? = null,
    val apiVersion: String? = null,
)
