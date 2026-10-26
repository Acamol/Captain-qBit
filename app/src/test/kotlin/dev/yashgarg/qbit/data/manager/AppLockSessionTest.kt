package dev.yashgarg.qbit.data.manager

import dev.yashgarg.qbit.data.models.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {

    private var clock = 0L

    private fun session(
        enabled: Boolean = true,
        timeoutMs: Long = 30_000L,
        lockOnScreenOff: Boolean = true,
    ) = AppLockSession(AppLockSettings(enabled, timeoutMs, lockOnScreenOff)) { clock }

    private fun AppLockSession.leaveFor(ms: Long) {
        onBackground()
        clock += ms
        onForeground()
    }

    @Test
    fun `a new session starts locked only when the lock is on`() {
        assertTrue(session().locked.value)
        assertFalse(session(enabled = false).locked.value)
    }

    @Test
    fun `locks once the app was in the background for the timeout`() {
        val session = session().apply { unlock() }

        session.leaveFor(29_999L)
        assertFalse(session.locked.value)

        session.leaveFor(30_000L)
        assertTrue(session.locked.value)
    }

    @Test
    fun `an immediate timeout locks as soon as the app is left`() {
        val session = session(timeoutMs = 0L).apply { unlock() }

        session.onBackground()

        assertTrue(session.locked.value)
    }

    @Test
    fun `the never timeout only locks when the app is opened afresh`() {
        val session = session(timeoutMs = AppLockSettings.TIMEOUT_NEVER).apply { unlock() }

        session.leaveFor(24 * 3_600_000L)
        assertFalse(session.locked.value)

        session.onAppOpened()
        assertTrue(session.locked.value)
    }

    @Test
    fun `turning the screen off locks only when that option is on`() {
        val locking = session().apply { unlock() }
        locking.onScreenOff()
        assertTrue(locking.locked.value)

        val notLocking = session(lockOnScreenOff = false).apply { unlock() }
        notLocking.onScreenOff()
        assertFalse(notLocking.locked.value)
    }

    /**
     * Below API 30 the prompt's screen-lock fallback is its own activity, so authenticating sends
     * the app to the background. Returning from it must not undo the unlock.
     */
    @Test
    fun `unlocking while the prompt hid the app survives the return to the foreground`() {
        val session = session(timeoutMs = 0L)

        session.onBackground()
        clock += 5_000L
        session.unlock()
        session.onForeground()

        assertFalse(session.locked.value)
    }

    @Test
    fun `turning the lock off unlocks, and turning it on doesn't lock`() {
        val session = session()

        session.settings = session.settings.copy(enabled = false)
        assertFalse(session.locked.value)
        session.onAppOpened()
        session.onScreenOff()
        assertFalse(session.locked.value)

        session.settings = session.settings.copy(enabled = true)
        assertFalse(session.locked.value)
    }

    @Test
    fun `a repeated background event doesn't push the start of the absence later`() {
        val session = session().apply { unlock() }

        session.onBackground()
        clock += 20_000L
        session.onBackground()
        clock += 15_000L
        session.onForeground()

        assertTrue(session.locked.value)
    }

    @Test
    fun `recreating the app while it is in use does not lock it again`() {
        val session = session().apply { unlock() }

        session.onAppOpened(appVisible = true)
        assertFalse(session.locked.value)

        session.onAppOpened(appVisible = false)
        assertTrue(session.locked.value)
    }

    private val strict = AppLockSettings(enabled = true, timeoutMs = 0L, lockOnScreenOff = true)

    @Test
    fun `turning the lock off weakens it`() {
        assertEquals(LockWeakening.TURNED_OFF, strict.weakeningTo(strict.copy(enabled = false)))
    }

    @Test
    fun `a longer timeout or no lock on screen off loosens the lock`() {
        assertEquals(LockWeakening.LOOSENED, strict.weakeningTo(strict.copy(timeoutMs = 30_000L)))
        assertEquals(
            LockWeakening.LOOSENED,
            strict.weakeningTo(strict.copy(timeoutMs = AppLockSettings.TIMEOUT_NEVER)),
        )
        assertEquals(
            LockWeakening.LOOSENED,
            strict.weakeningTo(strict.copy(lockOnScreenOff = false)),
        )
    }

    @Test
    fun `never is the longest timeout`() {
        val never = strict.copy(timeoutMs = AppLockSettings.TIMEOUT_NEVER)

        assertEquals(null, never.weakeningTo(never.copy(timeoutMs = 3_600_000L)))
    }

    @Test
    fun `a stricter or unchanged lock, or any change while it is off, isn't a weakening`() {
        val relaxed = strict.copy(timeoutMs = 60_000L, lockOnScreenOff = false)

        assertEquals(null, relaxed.weakeningTo(strict))
        assertEquals(null, strict.weakeningTo(strict))
        assertEquals(null, strict.copy(enabled = false).weakeningTo(relaxed.copy(enabled = false)))
    }

    @Test
    fun `any negative timeout is read as never and counts as a longer one`() {
        val settings =
            AppLockSettings.from(
                AppPreferences(appLockEnabled = true, appLockTimeoutMs = -2L),
            )

        assertEquals(AppLockSettings.TIMEOUT_NEVER, settings.timeoutMs)
        assertEquals(
            LockWeakening.LOOSENED,
            strict.weakeningTo(strict.copy(timeoutMs = -2L)),
        )
    }
}
