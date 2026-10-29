package dev.yashgarg.qbit

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.ui.common.LockScreenContent
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Covers the app while it's locked. An activity of its own rather than a layer over
 * [MainActivity]'s content, so it is above every window MainActivity has or opens later, dialogs
 * included. MainActivity starts it whenever it is shown while locked, and it finishes once the app
 * is unlocked.
 *
 * MainActivity is singleInstance, so this lives in a task of its own, kept out of the recents
 * screen: the app's one entry there stays MainActivity's.
 */
@AndroidEntryPoint
class LockActivity : AppCompatActivity() {

    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var appPrefsStore: DataStore<AppPreferences>

    // The prompt is shown by itself once each time this screen is shown - not again after the user
    // dismisses it, which would trap them in a loop.
    private var autoPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        autoPrompted = savedInstanceState?.getBoolean(STATE_AUTO_PROMPTED) ?: false
        appLockManager.attach(this)

        // Back leaves for the home screen, rather than revealing MainActivity's task behind this.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        setContent {
            val dynamicColorsFlow = remember { appPrefsStore.data.map { it.dynamicColors } }
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = false)
            QbitComposeTheme(dynamicColors = dynamicColors) {
                LockScreenContent(onUnlock = ::promptUnlock)
            }
        }

        lifecycleScope.launch {
            appLockManager.locked.first { !it }
            finish()
            disableTransition()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // A prompt already up (one the app opened before it locked) unlocks it too.
                if (!autoPrompted && !appLockManager.promptShowing) {
                    autoPrompted = true
                    promptUnlock()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLockManager.promptFailed.collect {
                    // Failing while the app is away means the prompt should show again on return.
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        autoPrompted = false
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving while locked means the prompt should show again on return. Not while the prompt
        // itself hides this screen, or after a rotation.
        if (!appLockManager.promptShowing && !isChangingConfigurations) autoPrompted = false
    }

    override fun onDestroy() {
        super.onDestroy()
        appLockManager.detach(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTO_PROMPTED, autoPrompted)
    }

    private fun promptUnlock() {
        appLockManager.authenticate(getString(CommonR.string.unlock_prompt_title))
    }

    private fun disableTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val STATE_AUTO_PROMPTED = "auto_prompted"

        /** Covers [activity]'s app with the lock screen. */
        fun start(activity: Activity) {
            activity.startActivity(
                Intent(activity, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
