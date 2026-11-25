package dev.yashgarg.qbit.ui.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.models.LogEntry

class LogFilterTest {
    private val info =
        LogEntry(id = 1, message = "Connected to server", timestamp = 1L, type = LogEntry.TYPE_INFO)
    private val warning =
        LogEntry(
            id = 2,
            message = "Disk space is low",
            timestamp = 2L,
            type = LogEntry.TYPE_WARNING,
        )
    private val critical =
        LogEntry(id = 3, message = "Disk I/O error", timestamp = 3L, type = LogEntry.TYPE_CRITICAL)
    private val entries = listOf(info, warning, critical)

    @Test
    fun `search combines trimmed case-insensitive text with severity`() {
        assertEquals(listOf(warning, critical), filterLogEntries(entries, " DISK ", null))
        assertEquals(listOf(critical), filterLogEntries(entries, " DISK ", LogSeverity.CRITICAL))
        assertTrue(filterLogEntries(entries, "connected", LogSeverity.CRITICAL).isEmpty())
    }

    @Test
    fun `clearing filters restores every level in source order`() {
        assertEquals(entries, filterLogEntries(entries, "   ", null))
        assertEquals(listOf(info), filterLogEntries(entries, "", LogSeverity.INFO))
    }
}
