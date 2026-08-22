package dev.yashgarg.qbit.ui.logs

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.ui.common.StatusViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LogsViewModel
@Inject
constructor(private val repository: QbitRepository, @ApplicationContext context: Context) :
    StatusViewModel(context) {
    private val _uiState = MutableStateFlow(LogsState())
    val uiState = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
            repository
                .getLogs()
                .onOk { entries ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            entries = entries,
                            error = null,
                        )
                    }
                }
                .onErr { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error =
                                error.friendlyMessage(
                                    getString(CommonR.string.status_load_logs_failure)
                                ),
                        )
                    }
                }
        }
    }
}
