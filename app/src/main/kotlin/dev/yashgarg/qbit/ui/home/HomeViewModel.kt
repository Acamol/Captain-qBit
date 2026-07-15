package dev.yashgarg.qbit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes config status to the Home composable so it shows a spinner until it's known. */
@HiltViewModel
class HomeViewModel @Inject constructor(clientManager: ClientManager) : ViewModel() {
    // null = not resolved yet (keep spinning); MainActivity routes to the list on EXISTS, so Home
    // only reveals its welcome content on DOES_NOT_EXIST.
    val configStatus: StateFlow<ConfigStatus?> =
        clientManager.configStatus.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null,
        )
}
