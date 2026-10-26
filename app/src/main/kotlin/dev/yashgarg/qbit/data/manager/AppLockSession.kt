package dev.yashgarg.qbit.data.manager

import dev.yashgarg.qbit.data.models.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How a change to the lock settings makes the lock weaker. */
enum class LockWeakening {
    TURNED_OFF,
    /**
     * Still on, but engaging less often: a longer timeout, or no longer on the screen turning off.
     */
    LOOSENED,
}

/** The app-lock preferences that decide when [AppLockSession] locks. */
data class AppLockSettings(
    val enabled: Boolean,
    val timeoutMs: Long,
    val lockOnScreenOff: Boolean,
) {
    /**
     * How moving to [next] weakens the lock, or null when it doesn't. A weaker lock has to be
     * confirmed by the user first, or someone holding the unlocked app could keep it from ever
     * locking again.
     */
    fun weakeningTo(next: AppLockSettings): LockWeakening? =
        when {
            !enabled -> null
            !next.enabled -> LockWeakening.TURNED_OFF
            next.timeoutRank > timeoutRank || (lockOnScreenOff && !next.lockOnScreenOff) ->
                LockWeakening.LOOSENED
            else -> null
        }

    /**
     * Whether the lock engages after [timeoutMs] in the background. Any negative timeout never
     * does.
     */
    val locksOnTimer: Boolean
        get() = timeoutMs >= 0

    private val timeoutRank: Long
        get() = if (locksOnTimer) timeoutMs else Long.MAX_VALUE

    companion object {
        /**
         * Never lock on a timer: only a fresh start of the app (or the screen turning off) does.
         */
        const val TIMEOUT_NEVER = -1L

        /**
         * Reads every negative timeout as [TIMEOUT_NEVER], which is what it means (see
         * [locksOnTimer]), so it is also shown as that. Preferences can come from a backup file.
         */
        fun from(prefs: AppPreferences) =
            AppLockSettings(
                prefs.appLockEnabled,
                prefs.appLockTimeoutMs.coerceAtLeast(TIMEOUT_NEVER),
                prefs.appLockOnScreenOff,
            )
    }
}

/**
 * Decides whether the app is locked, from the lock settings and the app moving between foreground
 * and background. Kept free of Android so the timing rules can be unit-tested; [AppLockManager]
 * feeds it the real events, all on the main thread.
 *
 * A new session starts locked when the lock is on, so a fresh start of the app always asks.
 */
class AppLockSession(initial: AppLockSettings, private val now: () -> Long) {
    private val _locked = MutableStateFlow(initial.enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    var settings: AppLockSettings = initial
        set(value) {
            field = value
            // Turning the lock on doesn't engage it: the user has just authenticated to do so.
            if (!value.enabled) {
                _locked.value = false
                backgroundedAt = null
            }
        }

    fun onBackground() {
        if (!settings.enabled) return
        // Kept from the first event: a repeat must not push the start of the absence later.
        if (backgroundedAt == null) backgroundedAt = now()
        // Engaged right away rather than on return, so the app is already covered when it's next
        // shown.
        if (settings.timeoutMs == 0L) _locked.value = true
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val timeout = settings.timeoutMs
        if (settings.enabled && settings.locksOnTimer && now() - since >= timeout) {
            _locked.value = true
        }
    }

    fun onScreenOff() {
        if (settings.enabled && settings.lockOnScreenOff) _locked.value = true
    }

    /**
     * The app was opened afresh (not just brought back), which always asks when the lock is on. An
     * [appVisible] app was merely recreated while in use (e.g. by tapping a notification), which
     * isn't a reason to ask again.
     */
    fun onAppOpened(appVisible: Boolean = false) {
        if (settings.enabled && !appVisible) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        // An unlock while the app was briefly hidden (by the prompt itself) must not be undone by
        // the return to the foreground that follows it.
        backgroundedAt = null
    }
}
