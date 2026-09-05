package dev.yashgarg.qbit.ui.common

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.LocalizedContext
import dev.yashgarg.qbit.utils.friendlyMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Base for ViewModels that report one-shot action outcomes as status toasts.
 *
 * Takes an [Application] rather than a bare `Context` so "this is the process-lifetime context, not
 * an Activity's" is enforced by the type instead of by convention — holding it for the ViewModel's
 * lifetime then can't leak.
 */
abstract class StatusViewModel(private val application: Application) : ViewModel() {
    private val _status = MutableSharedFlow<String>()
    val status = _status.asSharedFlow()

    // These messages are shown as toasts, so they have to honour the language chosen in the app -
    // which the Application context on its own does not below API 33. Resolved per lookup rather
    // than cached, so switching language takes effect without recreating the ViewModel.
    private val localized: Context
        get() = LocalizedContext.of(application)

    /** Resolves a string resource, for building [launchStatus]/[emitStatus] messages. */
    protected fun getString(resId: Int, vararg formatArgs: Any): String =
        localized.getString(resId, *formatArgs)

    /** Resolves a `<plurals>` resource, for building [launchStatus]/[emitStatus] messages. */
    protected fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any): String =
        localized.resources.getQuantityString(resId, quantity, *formatArgs)

    /**
     * Member overload of the top-level [dev.yashgarg.qbit.utils.friendlyMessage] resolving against
     * this ViewModel's own [application], so subclasses can keep calling
     * `throwable.friendlyMessage(fallback)` without threading a resolver through themselves.
     */
    protected fun Throwable.friendlyMessage(
        fallback: String = getString(CommonR.string.unknown_error)
    ): String = friendlyMessage({ getString(it) }, fallback)

    /** For call sites whose success/failure shape doesn't fit [launchStatus]. */
    protected suspend fun emitStatus(message: String) {
        _status.emit(message)
    }

    /**
     * Runs [action] and emits [successMessage] on [Ok] (after [onSuccess]), or the error's friendly
     * message (falling back to [failureMessage]) on [Err].
     */
    protected fun <V> launchStatus(
        successMessage: String,
        failureMessage: String,
        onSuccess: suspend (V) -> Unit = {},
        action: suspend () -> Result<V, Throwable>,
    ) {
        viewModelScope.launch {
            action()
                .onOk {
                    onSuccess(it)
                    _status.emit(successMessage)
                }
                .onErr { _status.emit(it.friendlyMessage(failureMessage)) }
        }
    }

    /**
     * Runs [block] only while the app process is at least [Lifecycle.State.STARTED] — i.e. actually
     * visible — cancelling it when the app backgrounds and restarting it when it returns. Screens
     * whose ViewModel outlives normal navigation (e.g. a permanent root screen) would otherwise
     * keep polling the server indefinitely in the background: Navigation Compose doesn't clear a
     * screen's ViewModel just because the Activity does.
     */
    protected suspend fun syncWhileForeground(block: suspend () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
