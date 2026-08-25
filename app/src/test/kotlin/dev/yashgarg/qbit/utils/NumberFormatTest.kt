package dev.yashgarg.qbit.utils

import android.content.Context
import android.content.res.Resources
import dev.yashgarg.qbit.common.R as CommonR
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NumberFormatTest {
    private val bytes = 1602083870L
    private val timeInSeconds = 136L
    private val timeInSecondsWithDays = 190_000L
    private val dateInMsEpoch = 1659354647L
    private val zoneId = ZoneId.of("GMT+05:30")

    // toHumanReadable()/toTime()/toDate() wrap their result in Unicode directional isolate marks
    // (U+2066/U+2069) so it renders correctly when embedded in RTL text - match that here.
    private fun isolated(s: String) = "\u2066$s\u2069"

    @Before
    fun setUp() {
        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(context.resources).thenReturn(resources)
        whenever(context.getString(CommonR.string.unit_bytes)).thenReturn("B")
        whenever(context.getString(CommonR.string.unit_kibibytes)).thenReturn("KiB")
        whenever(context.getString(CommonR.string.unit_mebibytes)).thenReturn("MiB")
        whenever(context.getString(CommonR.string.unit_gibibytes)).thenReturn("GiB")
        whenever(context.getString(CommonR.string.unit_tebibytes)).thenReturn("TiB")
        whenever(context.getString(CommonR.string.unit_pebibytes)).thenReturn("PiB")
        whenever(context.getString(CommonR.string.unit_exbibytes)).thenReturn("EiB")
        whenever(context.getString(CommonR.string.unit_hours_suffix)).thenReturn("h")
        whenever(context.getString(CommonR.string.unit_minutes_suffix)).thenReturn("m")
        whenever(context.getString(CommonR.string.unit_seconds_suffix)).thenReturn("s")
        whenever(resources.getQuantityString(eq(CommonR.plurals.unit_days_suffix), any()))
            .thenReturn("d")
        AppContextHolder.context = context
    }

    @Test
    fun testCorrectSizeIsValid() {
        assertTrue(bytes.toHumanReadable() == isolated("1.49 GiB"))
    }

    @Test
    fun testIncorrectSizeIsInvalid() {
        assertFalse(bytes.toHumanReadable() == isolated("1.1 GiB"))
    }

    @Test
    fun testMillisCorrectDateIsValid() {
        assertTrue(dateInMsEpoch.toDate(zoneId) == isolated("01/08/2022, 17:20:47"))
    }

    @Test
    fun testMillisIncorrectDateIsInvalid() {
        assertFalse(dateInMsEpoch.toDate(zoneId) == isolated("12/08/2021, 12:30"))
    }

    @Test
    fun testCorrectTimeIsValid() {
        assertTrue(timeInSeconds.toTime() == isolated("2m 16s"))
    }

    @Test
    fun testIncorrectTimeIsInvalid() {
        assertFalse(timeInSeconds.toTime() == isolated("5m 20s"))
    }

    @Test
    fun testTimeWithDaysUsesQuantityStringForDaySuffix() {
        assertTrue(timeInSecondsWithDays.toTime() == isolated("2d 4h 46m 40s"))
    }
}
