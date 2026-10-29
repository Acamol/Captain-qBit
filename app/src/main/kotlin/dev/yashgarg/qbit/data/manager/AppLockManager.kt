package dev.yashgarg.qbit.data.manager

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.di.ApplicationScope
import dev.yashgarg.qbit.utils.WindowPrivacy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The app lock: asks for the device's biometrics or screen lock (PIN, pattern or password) before
 * the app's content is shown. Owns the [AppLockSession] and feeds it the app moving to and from the
 * background and the screen turning off.
 */
@Singleton
class AppLockManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: DataStore<AppPreferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private lateinit var session: AppLockSession

    private class Handlers(val onSuccess: () -> Unit, val onFailure: () -> Unit)

    // One prompt per activity (a second on the same activity would displace the first's callback),
    // and who asked for the prompt in progress. Kept apart so the result reaches the askers even if
    // the activity, and so its prompt, is recreated meanwhile. Asking while a prompt is up doesn't
    // show another, so the result of the one on screen goes to everyone who asked.
    private val prompts = mutableListOf<Pair<FragmentActivity, BiometricPrompt>>()
    private val askers = mutableListOf<Handlers>()

    // The activity showing the prompt in progress. If it finishes, the prompt's result will never
    // come, and the askers are told it failed rather than waiting forever. Destroyed without
    // finishing - a rotation, or the system reclaiming it while the screen-lock fallback covers it
    // -
    // it is only replaced: the result reaches the new instance of the same activity.
    private var promptHost: FragmentActivity? = null
    private var promptHostRecreating: Class<out FragmentActivity>? = null

    private var startedActivities = 0

    private lateinit var _windowPrivacy: MutableStateFlow<WindowPrivacy>

    /** How the app's windows keep their content out of screenshots and the recents screen. */
    val windowPrivacy: StateFlow<WindowPrivacy>
        get() = _windowPrivacy

    /** Whether the system prompt is up. Main thread only. */
    var promptShowing = false
        private set

    private val _promptFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a prompt is cancelled or fails for good. */
    val promptFailed: SharedFlow<Unit> = _promptFailed.asSharedFlow()

    val locked: StateFlow<Boolean>
        get() = session.locked

    /** The lock settings in effect. */
    val settings: AppLockSettings
        get() = session.settings

    /** Called once from Application.onCreate with the already-loaded preferences. */
    fun init(prefs: AppPreferences) {
        session = AppLockSession(AppLockSettings.from(prefs), SystemClock::elapsedRealtime)
        _windowPrivacy = MutableStateFlow(prefs.windowPrivacy())
        // The session is only touched from the main thread.
        scope.launch(Dispatchers.Main) {
            prefsStore.data
                .map { AppLockSettings.from(it) }
                .distinctUntilChanged()
                .collect { session.settings = it }
        }
        scope.launch(Dispatchers.Main) {
            prefsStore.data.map { it.windowPrivacy() }.collect { _windowPrivacy.value = it }
        }
        // Counted here rather than taken from ProcessLifecycleOwner, whose stop comes about 700ms
        // late: a lock set to engage immediately would miss a quick switch away and back.
        //
        // Below API 30 the prompt's screen-lock fallback is an activity of another app, so
        // authenticating counts as leaving the app, and may engage the lock underneath. That's left
        // to happen: the lock screen doesn't prompt again while a prompt is up, and the prompt's
        // success unlocks it.
        (context as Application).registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (startedActivities++ == 0) session.onForeground()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    if (startedActivities == 0 && !activity.isChangingConfigurations) {
                        session.onBackground()
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = session.onScreenOff()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** The app's UI was opened afresh (not recreated), e.g. from the launcher after closing it. */
    fun onAppOpened() = session.onAppOpened(appVisible = startedActivities > 0)

    private fun AppPreferences.windowPrivacy() =
        WindowPrivacy.of(hideInRecents, blockScreenshots, appLockEnabled)

    /** Whether the device has a screen lock - without one there is nothing to unlock with. */
    fun isAvailable(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * If the screen lock was removed while the app lock was on, nothing could ever unlock the app,
     * so the lock is turned off instead. Returns true when it did so.
     */
    fun disableIfUnavailable(): Boolean {
        if (!session.settings.enabled || isAvailable()) return false
        session.settings = session.settings.copy(enabled = false)
        scope.launch { prefsStore.updateData { it.copy(appLockEnabled = false) } }
        return true
    }

    /**
     * Readies the system prompt on [activity]. Call it on every creation of the activity, not just
     * when prompting: a prompt that was up when the activity was recreated (e.g. by a rotation)
     * only delivers its result once one has been created again.
     */
    fun attach(activity: FragmentActivity) {
        if (promptShowing && promptHostRecreating == activity.javaClass) {
            promptHost = activity
            promptHostRecreating = null
        }
        prompts.removeAll { it.first === activity }
        prompts +=
            activity to
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult,
                        ) {
                            val askers = takeAskers()
                            // Also unlocks the app: the prompt may have engaged the lock
                            // underneath.
                            session.unlock()
                            askers.forEach { it.onSuccess() }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            failPrompt()
                        }
                    },
                )
    }

    /** Lets go of [activity] once it's gone, so it isn't kept alive. */
    fun detach(activity: FragmentActivity) {
        prompts.removeAll { it.first === activity }
        if (activity !== promptHost) return
        promptHost = null
        if (activity.isFinishing) {
            failPrompt()
        } else {
            promptHostRecreating = activity.javaClass
        }
    }

    /**
     * The prompt of the activity the user sees: the lock screen covers the app's main activity, and
     * a prompt shown on the covered one would be hidden behind it. A stopped activity's prompt
     * would never show at all.
     */
    private fun visiblePrompt(): Pair<FragmentActivity, BiometricPrompt>? =
        prompts.lastOrNull { it.first.lifecycle.currentState.isAtLeast(RESUMED) }
            ?: prompts.lastOrNull { it.first.lifecycle.currentState.isAtLeast(STARTED) }

    /**
     * Shows the system prompt. A success also unlocks the app. [onFailure] runs when the prompt is
     * cancelled or fails for good (individual failed attempts are retried by the prompt itself).
     */
    fun authenticate(title: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) {
        val (host, prompt) =
            visiblePrompt()
                ?: run {
                    onFailure()
                    return
                }
        val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Weak biometrics too, as many phones' face unlock is only that: nothing is decrypted
            // with the result, so a strong one adds nothing.
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            // On Android 9-10, setAllowedAuthenticators cannot combine device credentials with a
            // biometric. The legacy API supports both, including weak biometrics.
            @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        }
        askers += Handlers(onSuccess, onFailure)
        promptShowing = true
        promptHost = host
        prompt.authenticate(builder.build())
    }

    private fun failPrompt() {
        val askers = takeAskers()
        _promptFailed.tryEmit(Unit)
        askers.forEach { it.onFailure() }
    }

    private fun takeAskers(): List<Handlers> {
        promptShowing = false
        promptHost = null
        promptHostRecreating = null
        return askers.toList().also { askers.clear() }
    }
}
