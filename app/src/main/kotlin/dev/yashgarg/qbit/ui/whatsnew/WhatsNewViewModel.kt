package dev.yashgarg.qbit.ui.whatsnew

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.BuildConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhatsNewState(
    val visible: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
    val entries: List<String> = emptyList(),
)

@HiltViewModel
class WhatsNewViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: DataStore<ServerPreferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhatsNewState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSeen = prefsStore.data.first().lastSeenVersionCode
            val current = BuildConfig.VERSION_CODE

            // Only surface on an upgrade — never on a fresh install (lastSeen == 0) or a downgrade.
            if (lastSeen != 0 && current > lastSeen) {
                val entries = ChangelogAssets.read(context, current)
                if (entries.isNotEmpty()) {
                    _uiState.update { it.copy(visible = true, entries = entries) }
                }
            }

            // Record the current version so the dialog won't reappear until the next upgrade. Done
            // even when nothing is shown, so a fresh install starts tracking from here.
            if (lastSeen != current) {
                prefsStore.updateData { it.copy(lastSeenVersionCode = current) }
            }
        }
    }

    fun dismiss() {
        _uiState.update { it.copy(visible = false) }
    }
}
