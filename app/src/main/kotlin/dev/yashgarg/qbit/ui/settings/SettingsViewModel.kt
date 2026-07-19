package dev.yashgarg.qbit.ui.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.models.ServerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefsStore: DataStore<ServerPreferences>) :
    ViewModel() {

    val dynamicColors: StateFlow<Boolean> =
        prefsStore.data
            .map { it.dynamicColors }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<Int> =
        prefsStore.data.map { it.themeMode }.stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    val statusNotification: StateFlow<Boolean> =
        prefsStore.data
            .map { it.statusNotification }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notifyOnComplete: StateFlow<Boolean> =
        prefsStore.data
            .map { it.notifyOnComplete }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notifyOnChecked: StateFlow<Boolean> =
        prefsStore.data
            .map { it.notifyOnChecked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(dynamicColors = enabled) } }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { prefsStore.updateData { it.copy(themeMode = mode) } }
    }

    fun setStatusNotification(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(statusNotification = enabled) } }
    }

    fun setNotifyOnComplete(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(
                    notifyOnComplete = enabled,
                    // Enabling: tell the worker to re-baseline so past completions aren't replayed.
                    notifCompleteRebaseline = enabled || it.notifCompleteRebaseline,
                )
            }
        }
    }

    fun setNotifyOnChecked(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(
                    notifyOnChecked = enabled,
                    notifCheckedRebaseline = enabled || it.notifCheckedRebaseline,
                )
            }
        }
    }
}
