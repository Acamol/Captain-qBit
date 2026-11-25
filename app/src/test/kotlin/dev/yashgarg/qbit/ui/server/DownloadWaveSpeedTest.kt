package dev.yashgarg.qbit.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWaveSpeedTest {
    @Test
    fun motionTracksTheShareOfTheTorrentDownloadedPerSecond() {
        val slow = downloadWaveCyclesPerSecond(1_000_000, 1_000_000_000)
        val fast = downloadWaveCyclesPerSecond(10_000_000, 1_000_000_000)
        assertTrue(fast > slow)
        assertEquals(1f, fast, 0.001f)
        assertEquals(slow, downloadWaveCyclesPerSecond(2_000_000, 2_000_000_000), 0.001f)
        assertTrue(downloadWaveCyclesPerSecond(1_000_000, 2_000_000_000) < slow)
    }

    @Test
    fun zeroSpeedOrUnknownSizeDoesNotAnimate() {
        assertEquals(0f, downloadWaveCyclesPerSecond(0, 1_000), 0f)
        assertEquals(0f, downloadWaveCyclesPerSecond(-1, 1_000), 0f)
        assertEquals(0f, downloadWaveCyclesPerSecond(1_000, 0), 0f)
        assertEquals(0f, downloadWaveCyclesPerSecond(1_000, -1), 0f)
    }

    @Test
    fun extremeRatesStayVisibleAndBoundedWithoutOverflowing() {
        assertEquals(0.15f, downloadWaveCyclesPerSecond(1, Long.MAX_VALUE), 0f)
        assertEquals(2f, downloadWaveCyclesPerSecond(Long.MAX_VALUE, 1), 0f)
        assertEquals(2f, downloadWaveCyclesPerSecond(Long.MAX_VALUE, Long.MAX_VALUE), 0f)
    }
}
