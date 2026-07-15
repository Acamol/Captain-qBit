package dev.yashgarg.qbit.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LogsViewModel @Inject constructor(private val repository: QbitRepository) : ViewModel() {
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
                            error = error.friendlyMessage("Couldn't load the server log"),
                        )
                    }
                }
        }
    }
}
