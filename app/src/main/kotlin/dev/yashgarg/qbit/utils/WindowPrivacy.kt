package dev.yashgarg.qbit.utils

import android.os.Build

/** What the window should do to keep its content out of screenshots and the recents screen. */
data class WindowPrivacy(val secure: Boolean, val recentsScreenshotEnabled: Boolean) {
    companion object {
        /**
         * With the app lock on, the recents preview is hidden for as long as the lock is on: the
         * lock only engages a moment after the app is left, by which time the system has already
         * taken the snapshot of what was on screen.
         *
         * Before API 33 there's no way to blank only the recents preview, so hiding it takes
         * FLAG_SECURE, which blocks screenshots too.
         */
        fun of(
            hideInRecents: Boolean,
            blockScreenshots: Boolean,
            appLockEnabled: Boolean,
            sdk: Int = Build.VERSION.SDK_INT,
        ): WindowPrivacy {
            val hideRecents = hideInRecents || appLockEnabled
            val secure = blockScreenshots || (hideRecents && sdk < Build.VERSION_CODES.TIRAMISU)
            return WindowPrivacy(secure, recentsScreenshotEnabled = !hideRecents)
        }
    }
}
