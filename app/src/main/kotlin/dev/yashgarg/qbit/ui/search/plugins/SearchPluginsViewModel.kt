package dev.yashgarg.qbit.ui.search.plugins

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.ui.common.StatusViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qbittorrent.models.SearchPlugin

data class SearchPluginsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val plugins: List<SearchPlugin> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SearchPluginsViewModel
@Inject
constructor(private val repository: QbitRepository, application: Application) :
    StatusViewModel(application) {
    private val _uiState = MutableStateFlow(SearchPluginsState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        _uiState.update { it.copy(refreshing = true) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repository
                .getSearchPlugins()
                .onOk { plugins ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            plugins = plugins.sortedBy { p -> p.fullName.lowercase() },
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
                                    getString(CommonR.string.status_search_plugins_load_failure),
                                ),
                        )
                    }
                }
        }
    }

    fun setEnabled(name: String, enabled: Boolean) =
        launchStatus(
            successMessage =
                getString(
                    if (enabled) CommonR.string.status_search_plugin_enabled
                    else CommonR.string.status_search_plugin_disabled,
                    name,
                ),
            failureMessage = getString(CommonR.string.status_search_plugin_toggle_failure, name),
            onSuccess = { load() },
        ) {
            repository.enableSearchPlugins(listOf(name), enabled)
        }

    fun install(url: String) =
        launchStatus(
            successMessage = getString(CommonR.string.status_search_plugin_install_requested),
            failureMessage = getString(CommonR.string.status_search_plugin_install_failure),
            // The server installs asynchronously, so the list may not show it on the first read.
            onSuccess = { load() },
        ) {
            repository.installSearchPlugins(listOf(url))
        }

    fun uninstall(name: String) =
        launchStatus(
            successMessage = getString(CommonR.string.status_search_plugin_uninstalled, name),
            failureMessage = getString(CommonR.string.status_search_plugin_uninstall_failure, name),
            onSuccess = { load() },
        ) {
            repository.uninstallSearchPlugins(listOf(name))
        }

    fun updateAll() =
        launchStatus(
            successMessage = getString(CommonR.string.status_search_plugins_update_requested),
            failureMessage = getString(CommonR.string.status_search_plugins_update_failure),
            onSuccess = { load() },
        ) {
            repository.updateSearchPlugins()
        }
}
