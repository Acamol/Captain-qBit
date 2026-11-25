package dev.yashgarg.qbit.data.manager

import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onErr
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.activeServer
import dev.yashgarg.qbit.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
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
    private val prefsStore: DataStore<AppPreferences>,
    @ApplicationScope coroutineScope: CoroutineScope,
) : ClientManager {
    private val _configStatus =
        MutableSharedFlow<ConfigStatus>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val configStatus = _configStatus.asSharedFlow()

    private var client: QBittorrentClient? = null
    private var installedSettings: ClientSettings? = null
    private val clientMutex = Mutex()

    init {
        coroutineScope.launch { observeActiveServer() }
    }

    private fun settings() = clientSettings(configDao.getConfigs(), prefsStore.data)

    private suspend fun observeActiveServer() {
        withContext(Dispatchers.IO) {
            settings().collect { observed ->
                clientMutex.withLock {
                    // A switch or first request may already have installed a newer snapshot while
                    // this observer waited for the lock. Never reinstall the older client.
                    if (installedSettings != observed) install(settings().first())
                }
            }
        }
    }

    override suspend fun setActiveServer(id: Int) {
        withContext(Dispatchers.IO) {
            clientMutex.withLock {
                prefsStore.updateData { it.copy(activeServerId = id) }
                if (installedSettings != null) install(settings().first())
            }
        }
    }

    // Held from checking that another server is left to deleting: two deletes at once could
    // otherwise both pass the check and leave none.
    private val deleteMutex = Mutex()

    override suspend fun deleteServer(id: Int): Boolean = deleteMutex.withLock {
        withContext(Dispatchers.IO) {
            val configs = configDao.getConfigs().first()
            if (configs.size <= 1) return@withContext false
            val wasActive =
                configs.activeServer(prefsStore.data.first().activeServerId)?.configId == id
            configDao.deleteConfig(id)
            if (wasActive) {
                setActiveServer(configDao.getConfigs().first().firstOrNull()?.configId ?: -1)
            }
            true
        }
    }

    override suspend fun checkAndGetClient(): QBittorrentClient? {
        return withContext(Dispatchers.IO) {
            clientMutex.withLock { client ?: install(settings().first()) }
        }
    }

    private suspend fun install(settings: ClientSettings): QBittorrentClient? {
        val changed = installedSettings != settings
        if (!changed && (client != null || settings.config == null)) return client
        client?.close()
        client = null
        installedSettings = settings
        val config = settings.config
        client =
            if (config == null) null
            else
                runSuspendCatching {
                    val port = if (config.port != null) ":${config.port}" else ""
                    val path = config.path ?: ""

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
                        apiKey = CryptoManager.decrypt(config.apiKey),
                        syncInterval = settings.interval.milliseconds,
                        httpClient = ClientManager.httpClient(basicAuth, pinnedCertificateDer),
                        dispatcher = Dispatchers.Default,
                    )
                }
                    .onErr { Log.e(this::class.simpleName, it.toString()) }
                    .get()
        // Publish after installation, including when an invalid config is edited into a valid one.
        if (changed || client != null) {
            _configStatus.tryEmit(
                if (config == null) ConfigStatus.DOES_NOT_EXIST else ConfigStatus.EXISTS,
            )
        }
        return client
    }
}

internal data class ClientSettings(val config: ServerConfig?, val interval: Long)

internal fun clientSettings(
    configs: Flow<List<ServerConfig>>,
    prefs: Flow<AppPreferences>,
): Flow<ClientSettings> =
    combine(configs, prefs.map { it.activeServerId to it.syncIntervalMs }.distinctUntilChanged()) {
            servers,
            (id, interval) ->
            ClientSettings(servers.activeServer(id)?.copy(position = 0, serverName = ""), interval)
        }
        .distinctUntilChanged()
