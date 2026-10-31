package dev.yashgarg.qbit.ui.serverlist

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.activeServer
import dev.yashgarg.qbit.ui.common.StatusViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ServerListViewModel
@Inject
constructor(
    private val prefsStore: DataStore<AppPreferences>,
    private val configDao: ConfigDao,
    private val clientManager: ClientManager,
    application: Application,
) : StatusViewModel(application) {

    val servers: StateFlow<List<ServerConfig>> =
        configDao.getConfigs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The id of the server the app is connected to, or -1 when there is none. */
    val activeServerId: StateFlow<Int> =
        combine(configDao.getConfigs(), prefsStore.data.map { it.activeServerId }) { configs, id ->
                configs.activeServer(id)?.configId ?: -1
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    /** Persists a drag-to-reorder result: [orderedIds] is the full server list in its new order. */
    fun reorderServers(orderedIds: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            orderedIds.forEachIndexed { index, id -> configDao.updatePosition(id, index) }
        }
    }

    fun deleteServer(id: Int) {
        viewModelScope.launch {
            if (!clientManager.deleteServer(id)) {
                emitStatus(getString(CommonR.string.status_cannot_delete_last_server))
            }
        }
    }
}
