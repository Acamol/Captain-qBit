package dev.yashgarg.qbit.ui.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.ServerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val prefsStore: DataStore<ServerPreferences>,
    private val repository: QbitRepository,
) : ViewModel() {

    private val _autoTmmEnabled = MutableStateFlow(false)

    /**
     * qBittorrent's own global default Auto Torrent Management setting (server-side, not local).
     */
    val autoTmmEnabled: StateFlow<Boolean> = _autoTmmEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAutoTmmEnabled().onOk { _autoTmmEnabled.value = it }
        }
    }

    fun setAutoTmmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoTmmEnabled(enabled).onOk { _autoTmmEnabled.value = enabled }
        }
    }

    private val _rssRefreshIntervalMinutes = MutableStateFlow(30)

    /** How often qBittorrent itself re-fetches RSS feeds, in minutes (server-side, not local). */
    val rssRefreshIntervalMinutes: StateFlow<Int> = _rssRefreshIntervalMinutes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRssRefreshInterval().onOk { _rssRefreshIntervalMinutes.value = it }
        }
    }

    fun setRssRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            repository.setRssRefreshInterval(minutes).onOk {
                _rssRefreshIntervalMinutes.value = minutes
            }
        }
    }

    private val _rssMaxArticlesPerFeed = MutableStateFlow(50)

    /** Maximum number of articles qBittorrent keeps per RSS feed (server-side, not local). */
    val rssMaxArticlesPerFeed: StateFlow<Int> = _rssMaxArticlesPerFeed.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRssMaxArticlesPerFeed().onOk { _rssMaxArticlesPerFeed.value = it }
        }
    }

    fun setRssMaxArticlesPerFeed(count: Int) {
        viewModelScope.launch {
            repository.setRssMaxArticlesPerFeed(count).onOk { _rssMaxArticlesPerFeed.value = count }
        }
    }

    private val _rssProcessingEnabled = MutableStateFlow(false)

    /** Whether qBittorrent fetches RSS feeds at all (server-side, not local). */
    val rssProcessingEnabled: StateFlow<Boolean> = _rssProcessingEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRssProcessingEnabled().onOk { _rssProcessingEnabled.value = it }
        }
    }

    fun setRssProcessingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRssProcessingEnabled(enabled).onOk {
                _rssProcessingEnabled.value = enabled
            }
        }
    }

    private val _rssAutoDownloadingEnabled = MutableStateFlow(false)

    /** Whether qBittorrent's RSS rules auto-download matches (server-side, not local). */
    val rssAutoDownloadingEnabled: StateFlow<Boolean> = _rssAutoDownloadingEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRssAutoDownloadingEnabled().onOk {
                _rssAutoDownloadingEnabled.value = it
            }
        }
    }

    fun setRssAutoDownloadingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRssAutoDownloadingEnabled(enabled).onOk {
                _rssAutoDownloadingEnabled.value = enabled
            }
        }
    }

    private val _queueingEnabled = MutableStateFlow(false)

    /** Whether the server caps how many torrents are active at once (server-side, not local). */
    val queueingEnabled: StateFlow<Boolean> = _queueingEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isQueueingEnabled().onOk { _queueingEnabled.value = it }
        }
    }

    fun setQueueingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setQueueingEnabled(enabled).onOk { _queueingEnabled.value = enabled }
        }
    }

    private val _speedLimitMode = MutableStateFlow(0)

    /** 0 = normal speed limits, nonzero = alternate speed limits are active. */
    val speedLimitMode: StateFlow<Int> = _speedLimitMode.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSpeedLimitMode().onOk { _speedLimitMode.value = it }
        }
    }

    fun toggleSpeedLimits() {
        viewModelScope.launch {
            repository.toggleSpeedLimitsMode().onOk {
                repository.getSpeedLimitMode().onOk { _speedLimitMode.value = it }
            }
        }
    }

    private val _globalDownloadLimit = MutableStateFlow(0)
    private val _globalUploadLimit = MutableStateFlow(0)
    private val _altDownloadLimit = MutableStateFlow(0)
    private val _altUploadLimit = MutableStateFlow(0)

    /** Server-wide speed limits, in bytes/s (0 = unlimited). */
    val globalDownloadLimit: StateFlow<Int> = _globalDownloadLimit.asStateFlow()

    val globalUploadLimit: StateFlow<Int> = _globalUploadLimit.asStateFlow()

    /** The limits used while "use alternate speed limits" is on, in bytes/s (0 = unlimited). */
    val altDownloadLimit: StateFlow<Int> = _altDownloadLimit.asStateFlow()

    val altUploadLimit: StateFlow<Int> = _altUploadLimit.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getGlobalDownloadLimit().onOk { _globalDownloadLimit.value = it }
            repository.getGlobalUploadLimit().onOk { _globalUploadLimit.value = it }
            repository.getAltSpeedLimits().onOk { (dl, ul) ->
                _altDownloadLimit.value = dl
                _altUploadLimit.value = ul
            }
        }
    }

    /** Limits are in bytes/s; 0 clears the limit (unlimited). */
    fun setGlobalLimits(downloadBytesPerSec: Int, uploadBytesPerSec: Int) {
        viewModelScope.launch {
            val dlOk = repository.setGlobalDownloadLimit(downloadBytesPerSec).get() != null
            val ulOk = repository.setGlobalUploadLimit(uploadBytesPerSec).get() != null
            if (dlOk && ulOk) {
                _globalDownloadLimit.value = downloadBytesPerSec
                _globalUploadLimit.value = uploadBytesPerSec
            }
        }
    }

    /** Alternate limits are in bytes/s; 0 clears the limit (unlimited). */
    fun setAltLimits(downloadBytesPerSec: Int, uploadBytesPerSec: Int) {
        viewModelScope.launch {
            repository.setAltSpeedLimits(downloadBytesPerSec, uploadBytesPerSec).onOk {
                _altDownloadLimit.value = downloadBytesPerSec
                _altUploadLimit.value = uploadBytesPerSec
            }
        }
    }

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

    val notifyOnNewRssArticles: StateFlow<Boolean> =
        prefsStore.data
            .map { it.notifyOnNewRssArticles }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val statusRefreshIntervalMs: StateFlow<Long> =
        prefsStore.data
            .map { it.statusRefreshIntervalMs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5_000L)

    val eventPollIntervalMs: StateFlow<Long> =
        prefsStore.data
            .map { it.eventPollIntervalMs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5_000L)

    val syncIntervalMs: StateFlow<Long> =
        prefsStore.data
            .map { it.syncIntervalMs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5_000L)

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

    fun setNotifyOnNewRssArticles(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(
                    notifyOnNewRssArticles = enabled,
                    notifRssRebaseline = enabled || it.notifRssRebaseline,
                )
            }
        }
    }

    fun setStatusRefreshIntervalMs(ms: Long) {
        viewModelScope.launch { prefsStore.updateData { it.copy(statusRefreshIntervalMs = ms) } }
    }

    fun setEventPollIntervalMs(ms: Long) {
        viewModelScope.launch { prefsStore.updateData { it.copy(eventPollIntervalMs = ms) } }
    }

    fun setSyncIntervalMs(ms: Long) {
        viewModelScope.launch { prefsStore.updateData { it.copy(syncIntervalMs = ms) } }
    }
}
