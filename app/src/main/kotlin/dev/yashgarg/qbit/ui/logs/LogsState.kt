package dev.yashgarg.qbit.ui.logs

import qbittorrent.models.LogEntry

data class LogsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val entries: List<LogEntry> = emptyList(),
    val error: String? = null,
)
