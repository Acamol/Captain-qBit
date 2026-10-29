package dev.yashgarg.qbit.utils

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowPrivacyTest {

    private val old = Build.VERSION_CODES.S

    private fun privacy(
        hideInRecents: Boolean = false,
        blockScreenshots: Boolean = false,
        appLockEnabled: Boolean = false,
        sdk: Int = Build.VERSION_CODES.TIRAMISU,
    ) = WindowPrivacy.of(hideInRecents, blockScreenshots, appLockEnabled, sdk)

    @Test
    fun `nothing is hidden by default`() {
        val privacy = privacy()

        assertFalse(privacy.secure)
        assertTrue(privacy.recentsScreenshotEnabled)
    }

    @Test
    fun `from API 33 the app lock hides the recents preview but allows screenshots`() {
        val privacy = privacy(appLockEnabled = true)

        assertFalse(privacy.recentsScreenshotEnabled)
        assertFalse(privacy.secure)
    }

    @Test
    fun `before API 33 the app lock takes the secure flag all the time`() {
        assertTrue(privacy(appLockEnabled = true, sdk = old).secure)
    }

    @Test
    fun `before API 33 hiding the recents preview takes the secure flag`() {
        assertTrue(privacy(hideInRecents = true, sdk = old).secure)
    }

    @Test
    fun `from API 33 hiding the recents preview leaves screenshots allowed`() {
        assertFalse(privacy(hideInRecents = true).secure)
    }

    @Test
    fun `blocking screenshots is always secure`() {
        assertTrue(privacy(blockScreenshots = true).secure)
        assertTrue(privacy(blockScreenshots = true, sdk = old).secure)
    }
}
