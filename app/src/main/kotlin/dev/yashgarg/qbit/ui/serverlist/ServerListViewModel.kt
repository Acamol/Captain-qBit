package dev.yashgarg.qbit.ui.serverlist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.ui.common.StatusViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ServerListViewModel
@Inject
constructor(
    private val prefsStore: DataStore<ServerPreferences>,
    private val configDao: ConfigDao,
    private val clientManager: ClientManager,
    @ApplicationContext context: Context,
) : StatusViewModel(context) {

    val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeServerId: StateFlow<Int> =
        prefsStore.data
            .map { it.activeServerId }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    // Mirrors ServerViewModel.deleteServer exactly: block deleting the last server, and if the
    // deleted one was active, fall back to whatever remains.
    fun deleteServer(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (configDao.getConfigs().first().size <= 1) {
                emitStatus(getString(CommonR.string.status_cannot_delete_last_server))
                return@launch
            }
            configDao.deleteConfig(id)
            if (id == prefsStore.data.first().activeServerId) {
                val remaining = configDao.getConfigs().first().firstOrNull()
                clientManager.setActiveServer(remaining?.configId ?: -1)
            }
        }
    }
}
