package dev.yashgarg.qbit.utils

import dev.yashgarg.qbit.data.models.EventAlertMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `a window inside one day covers only that stretch`() {
        val start = at(13)
        val end = at(14)
        assertFalse(isWithinQuietHours(at(12, 59), start, end))
        assertTrue(isWithinQuietHours(at(13), start, end))
        assertTrue(isWithinQuietHours(at(13, 59), start, end))
        assertFalse(isWithinQuietHours(at(14), start, end))
    }

    @Test
    fun `a window across midnight covers the evening and the next morning`() {
        // The usual case, and the one a naive start-to-end comparison gets backwards.
        val start = at(22)
        val end = at(7)
        assertTrue(isWithinQuietHours(at(22), start, end))
        assertTrue(isWithinQuietHours(at(23, 59), start, end))
        assertTrue(isWithinQuietHours(at(0), start, end))
        assertTrue(isWithinQuietHours(at(2), start, end))
        assertTrue(isWithinQuietHours(at(6, 59), start, end))
    }

    @Test
    fun `daytime is not quiet under a window across midnight`() {
        val start = at(22)
        val end = at(7)
        assertFalse(isWithinQuietHours(at(7), start, end))
        assertFalse(isWithinQuietHours(at(12), start, end))
        assertFalse(isWithinQuietHours(at(21, 59), start, end))
    }

    @Test
    fun `the window includes its start and excludes its end`() {
        // Half-open, so a window ending at 07:00 is over at 07:00 rather than a minute later.
        assertTrue(isWithinQuietHours(at(22), at(22), at(7)))
        assertFalse(isWithinQuietHours(at(7), at(22), at(7)))
    }

    @Test
    fun `equal start and end is an empty window rather than the whole day`() {
        for (h in 0..23) {
            assertFalse("$h:00 should not be quiet", isWithinQuietHours(at(h), at(9), at(9)))
        }
    }

    @Test
    fun `a window can span almost the whole day`() {
        val start = at(0, 1)
        val end = at(0)
        // start > end, so this wraps: quiet from 00:01 right round to 00:00.
        assertTrue(isWithinQuietHours(at(0, 1), start, end))
        assertTrue(isWithinQuietHours(at(12), start, end))
        assertTrue(isWithinQuietHours(at(23, 59), start, end))
        assertFalse(isWithinQuietHours(at(0), start, end))
    }

    @Test
    fun `midnight boundaries behave`() {
        // A window starting exactly at midnight does not wrap.
        assertTrue(isWithinQuietHours(at(0), at(0), at(6)))
        assertFalse(isWithinQuietHours(at(23, 59), at(0), at(6)))
        // One ending exactly at midnight does.
        assertTrue(isWithinQuietHours(at(23, 59), at(23), at(0)))
        assertFalse(isWithinQuietHours(at(0), at(23), at(0)))
    }

    @Test
    fun `every minute of the day is covered exactly once by a window and its complement`() {
        val start = at(22)
        val end = at(7)
        val quiet = (0 until MINUTES_PER_DAY).count { isWithinQuietHours(it, start, end) }
        // 22:00-24:00 is 120 minutes, 00:00-07:00 is 420.
        assertTrue("expected 540 quiet minutes, got $quiet", quiet == 540)
    }

    @Test
    fun `alert mode ALWAYS never silences, whatever the window says`() {
        val inside = at(23)
        assertFalse(shouldSilenceEventAlert(EventAlertMode.ALWAYS, inside, at(22), at(7)))
    }

    @Test
    fun `alert mode NEVER silences at every hour, ignoring the window`() {
        // The window is deliberately one that excludes most of the day - it must not matter.
        for (h in 0..23) {
            assertTrue(
                "$h:00 should be silenced",
                shouldSilenceEventAlert(EventAlertMode.NEVER, at(h), at(22), at(7)),
            )
        }
    }

    @Test
    fun `alert mode OUTSIDE_QUIET_HOURS follows the window`() {
        val mode = EventAlertMode.OUTSIDE_QUIET_HOURS
        assertTrue(shouldSilenceEventAlert(mode, at(23), at(22), at(7)))
        assertTrue(shouldSilenceEventAlert(mode, at(3), at(22), at(7)))
        assertFalse(shouldSilenceEventAlert(mode, at(12), at(22), at(7)))
        assertFalse(shouldSilenceEventAlert(mode, at(7), at(22), at(7)))
    }
}
