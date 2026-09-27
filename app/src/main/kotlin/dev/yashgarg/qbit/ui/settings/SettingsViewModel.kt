package dev.yashgarg.qbit.ui.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.EventAlertMode
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import qbittorrent.models.AltSpeedSchedule

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val prefsStore: DataStore<AppPreferences>,
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

    /** The last mode the server reported, through the sync stream or a direct read. */
    private val reportedSpeedLimitMode = MutableStateFlow(0)

    /** The mode we asked for, shown until the toggle's round trip has settled. */
    private val pendingSpeedLimitMode = MutableStateFlow<Int?>(null)

    private var togglesInFlight = 0

    /**
     * Set when a toggle settles: the next poll may have been sent before it did, so it would carry
     * the old mode.
     */
    private var discardNextPoll = false

    /**
     * 0 = normal speed limits, nonzero = alternate speed limits are active.
     *
     * The schedule flips this on the server with nothing to tell us, so it follows the sync stream
     * rather than only the values we write ourselves. That stream polls the server, so it only runs
     * while something collects this: the Speed limits screen, while it is visible.
     */
    val speedLimitMode: StateFlow<Int> =
        channelFlow {
                launch { followServerSpeedLimitMode() }
                combine(reportedSpeedLimitMode, pendingSpeedLimitMode) { reported, pending ->
                        pending ?: reported
                    }
                    .collect { send(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), 0)

    private suspend fun followServerSpeedLimitMode() {
        discardNextPoll = false
        repository
            .observeMainData()
            .map { if (it.serverState.useAltSpeedLimits) 1 else 0 }
            // Retry rather than catch: catching ends the flow, so one blip would stop the switch
            // tracking the server for good. Retrying also re-resolves the client, which is what
            // picks up a server switch.
            .retry {
                delay(SYNC_RETRY_MS)
                true
            }
            .collect { serverMode ->
                // While a toggle is in flight, a poll may predate it and would bounce the switch
                // back; the toggle settles the value itself once it completes.
                when {
                    togglesInFlight > 0 -> Unit
                    discardNextPoll -> discardNextPoll = false
                    else -> reportedSpeedLimitMode.value = serverMode
                }
            }
    }

    fun toggleSpeedLimits() {
        // The server only offers a toggle, so ask for the opposite of what is shown.
        pendingSpeedLimitMode.value = if (speedLimitMode.value == 0) 1 else 0
        togglesInFlight++
        viewModelScope.launch {
            try {
                repository.toggleSpeedLimitsMode()
                // Read the result back rather than trusting the target: the server may have moved
                // on its own since the switch was last updated.
                repository.getSpeedLimitMode().onOk { reportedSpeedLimitMode.value = it }
            } finally {
                if (--togglesInFlight == 0) {
                    pendingSpeedLimitMode.value = null
                    discardNextPoll = true
                }
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

    private val _altSpeedSchedule = MutableStateFlow<AltSpeedSchedule?>(null)

    /** The schedule the server last confirmed, restored when a write fails. */
    private var confirmedAltSpeedSchedule: AltSpeedSchedule? = null

    /** Every write sends the whole schedule, so they go out one at a time, in order. */
    private val altSpeedScheduleWrites = Mutex()

    /**
     * When the server switches to its alternate limits, and on which days. Null until it has been
     * read: every write sends the whole schedule, so editing before then would overwrite the
     * server's with placeholders. The read is retried while the Speed limits screen is visible.
     */
    val altSpeedSchedule: StateFlow<AltSpeedSchedule?> =
        channelFlow {
                launch { loadAltSpeedSchedule() }
                _altSpeedSchedule.collect { send(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    private suspend fun loadAltSpeedSchedule() {
        while (_altSpeedSchedule.value == null) {
            val loaded =
                repository
                    .getAltSpeedSchedule()
                    .onOk {
                        confirmedAltSpeedSchedule = it
                        _altSpeedSchedule.value = it
                    }
                    .isOk
            if (!loaded) delay(SYNC_RETRY_MS)
        }
    }

    /**
     * Shown at once, so an edit made before the previous write returns builds on it rather than on
     * the server's older copy.
     */
    fun setAltSpeedSchedule(schedule: AltSpeedSchedule) {
        _altSpeedSchedule.value = schedule
        viewModelScope.launch {
            altSpeedScheduleWrites.withLock {
                repository
                    .setAltSpeedSchedule(schedule)
                    .onOk { confirmedAltSpeedSchedule = schedule }
                    .onErr {
                        // A newer edit is already queued and will send its own copy.
                        if (_altSpeedSchedule.value == schedule) {
                            _altSpeedSchedule.value = confirmedAltSpeedSchedule
                        }
                    }
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

    /** [tag] is a BCP-47 language tag, or "" to follow the system locale. */
    fun setLanguageTag(tag: String) {
        viewModelScope.launch { prefsStore.updateData { it.copy(languageTag = tag) } }
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

    val eventAlertMode: StateFlow<EventAlertMode> =
        prefsStore.data
            .map { it.eventAlertMode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, EventAlertMode.ALWAYS)

    val quietHoursStartMinutes: StateFlow<Int> =
        prefsStore.data
            .map { it.quietHoursStartMinutes }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 22 * 60)

    val quietHoursEndMinutes: StateFlow<Int> =
        prefsStore.data
            .map { it.quietHoursEndMinutes }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 7 * 60)

    fun setEventAlertMode(mode: EventAlertMode) {
        viewModelScope.launch { prefsStore.updateData { it.copy(eventAlertMode = mode) } }
    }

    /** Both are minutes since midnight. */
    fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            prefsStore.updateData {
                it.copy(quietHoursStartMinutes = startMinutes, quietHoursEndMinutes = endMinutes)
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

/** Matches the torrent list's sync retry pacing. */
private const val SYNC_RETRY_MS = 5000L

/** Keeps the poll alive across a configuration change, which re-subscribes within moments. */
private const val SUBSCRIPTION_TIMEOUT_MS = 5000L
