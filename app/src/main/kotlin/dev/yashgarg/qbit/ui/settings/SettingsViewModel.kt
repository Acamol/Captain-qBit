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

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { prefsStore.updateData { it.copy(dynamicColors = enabled) } }
    }
}
