package dev.yashgarg.qbit.data.manager

import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onErr
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import qbittorrent.QBittorrentClient

@Singleton
class ClientManagerImpl
@Inject
constructor(
    private val configDao: ConfigDao,
    private val prefsStore: DataStore<ServerPreferences>,
    @ApplicationScope coroutineScope: CoroutineScope,
) : ClientManager {
    private val _configStatus = MutableSharedFlow<ConfigStatus>(replay = 1)
    override val configStatus = _configStatus.asSharedFlow()

    private var client: QBittorrentClient? = null
    private val clientMutex = Mutex()

    init {
        coroutineScope.launch { observeActiveServer() }
    }

    // Rebuild the client whenever the set of configs, the active server, OR the sync interval
    // changes. Only that slice of the prefs is observed, so unrelated pref writes don't churn it.
    private suspend fun observeActiveServer() {
        withContext(Dispatchers.IO) {
            combine(
                    configDao.getConfigs(),
                    prefsStore.data
                        .map { it.activeServerId to it.syncIntervalMs }
                        .distinctUntilChanged(),
                ) { configs, _ ->
                    configs
                }
                .collect { configs ->
                    dropClient() // force re-create with the (possibly new) active config
                    if (configs.isNotEmpty()) {
                        _configStatus.emit(ConfigStatus.EXISTS)
                        checkAndGetClient()
                    } else {
                        _configStatus.emit(ConfigStatus.DOES_NOT_EXIST)
                    }
                }
        }
    }

    override suspend fun setActiveServer(id: Int) {
        dropClient()
        prefsStore.updateData { it.copy(activeServerId = id) }
    }

    private suspend fun dropClient() {
        clientMutex.withLock {
            client?.close()
            client = null
        }
    }

    override suspend fun checkAndGetClient(): QBittorrentClient? {
        return runSuspendCatching { getClient() }
            .onErr { Log.e(this::class.simpleName, it.toString()) }
            .get()
    }

    // The active config is the one whose id matches the stored activeServerId; falls back to the
    // first config so existing single-server users (activeServerId still -1) and deletion of the
    // active server both resolve to a real server.
    private suspend fun resolveActiveConfig(): ServerConfig? {
        val configs = configDao.getConfigs().first()
        if (configs.isEmpty()) return null
        val activeId = prefsStore.data.first().activeServerId
        return configs.find { it.configId == activeId } ?: configs.first()
    }

    private suspend fun getClient(): QBittorrentClient = clientMutex.withLock {
        client?.let {
            return@withLock it
        }
        withContext(Dispatchers.IO) {
            val config = requireNotNull(resolveActiveConfig()) { "No server config" }
            val port = if (config.port != null) ":${config.port}" else ""
            val path = config.path ?: ""
            val syncIntervalMs = prefsStore.data.first().syncIntervalMs

            val basicAuth =
                if (
                    !config.basicAuthUsername.isNullOrEmpty() &&
                        !config.basicAuthPassword.isNullOrEmpty()
                ) {
                    config.basicAuthUsername to
                        (CryptoManager.decrypt(config.basicAuthPassword)
                            ?: config.basicAuthPassword)
                } else null
            val pinnedCertificateDer =
                config.pinnedCertificate?.let { Base64.decode(it, Base64.NO_WRAP) }

            QBittorrentClient(
                    "${config.connectionType.toString().lowercase()}://${config.baseUrl}$port$path",
                    config.username,
                    CryptoManager.decrypt(config.password) ?: config.password,
                    syncInterval = syncIntervalMs.milliseconds,
                    httpClient = ClientManager.httpClient(basicAuth, pinnedCertificateDer),
                    dispatcher = Dispatchers.Default,
                )
                .also { client = it }
        }
    }
}
