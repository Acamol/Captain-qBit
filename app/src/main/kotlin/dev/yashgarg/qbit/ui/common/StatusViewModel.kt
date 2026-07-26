package dev.yashgarg.qbit.ui.common

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.utils.friendlyMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Base for ViewModels that report one-shot action outcomes as status toasts. */
abstract class StatusViewModel(private val context: Context) : ViewModel() {
    private val _status = MutableSharedFlow<String>()
    val status = _status.asSharedFlow()

    /** Resolves a string resource, for building [launchStatus]/[emitStatus] messages. */
    protected fun getString(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

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
