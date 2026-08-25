package dev.yashgarg.qbit.utils

import android.content.Context
import dev.yashgarg.qbit.common.R as CommonR
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NumberFormatTest {
    private val bytes = 1602083870L
    private val timeInSeconds = 136L
    private val dateInMsEpoch = 1659354647L
    private val zoneId = ZoneId.of("GMT+05:30")

    @Before
    fun setUp() {
        val context = mock<Context>()
        whenever(context.getString(CommonR.string.unit_bytes)).thenReturn("B")
        whenever(context.getString(CommonR.string.unit_kibibytes)).thenReturn("KiB")
        whenever(context.getString(CommonR.string.unit_mebibytes)).thenReturn("MiB")
        whenever(context.getString(CommonR.string.unit_gibibytes)).thenReturn("GiB")
        whenever(context.getString(CommonR.string.unit_tebibytes)).thenReturn("TiB")
        whenever(context.getString(CommonR.string.unit_pebibytes)).thenReturn("PiB")
        whenever(context.getString(CommonR.string.unit_exbibytes)).thenReturn("EiB")
        whenever(context.getString(CommonR.string.unit_days_suffix)).thenReturn("d")
        whenever(context.getString(CommonR.string.unit_hours_suffix)).thenReturn("h")
        whenever(context.getString(CommonR.string.unit_minutes_suffix)).thenReturn("m")
        whenever(context.getString(CommonR.string.unit_seconds_suffix)).thenReturn("s")
        AppContextHolder.context = context
    }

    @Test
    fun testCorrectSizeIsValid() {
        assertTrue(bytes.toHumanReadable() == "1.49 GiB")
    }

    @Test
    fun testIncorrectSizeIsInvalid() {
        assertFalse(bytes.toHumanReadable() == "1.1 GiB")
    }

    @Test
    fun testMillisCorrectDateIsValid() {
        assertTrue(dateInMsEpoch.toDate(zoneId) == "01/08/2022, 17:20:47")
    }

    @Test
    fun testMillisIncorrectDateIsInvalid() {
        assertFalse(dateInMsEpoch.toDate(zoneId) == "12/08/2021, 12:30")
    }

    @Test
    fun testCorrectTimeIsValid() {
        assertTrue(timeInSeconds.toTime() == "2m 16s")
    }

    @Test
    fun testIncorrectTimeIsInvalid() {
        assertFalse(timeInSeconds.toTime() == "5m 20s")
    }
}
