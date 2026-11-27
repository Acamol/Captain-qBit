package dev.yashgarg.qbit.ui.logs

import qbittorrent.models.LogEntry

internal enum class LogSeverity(val type: Int) {
    NORMAL(LogEntry.TYPE_NORMAL),
    INFO(LogEntry.TYPE_INFO),
    WARNING(LogEntry.TYPE_WARNING),
    CRITICAL(LogEntry.TYPE_CRITICAL);

    companion object {
        fun fromType(type: Int): LogSeverity = entries.find { it.type == type } ?: NORMAL
    }
}

internal fun filterLogEntries(
    entries: List<LogEntry>,
    query: String,
    severity: LogSeverity?,
): List<LogEntry> {
    val search = query.trim()
    return entries.filter {
        (severity == null || LogSeverity.fromType(it.type) == severity) &&
            it.message.contains(search, ignoreCase = true)
    }
}
