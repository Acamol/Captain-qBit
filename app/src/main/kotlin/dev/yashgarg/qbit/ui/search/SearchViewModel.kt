package dev.yashgarg.qbit.ui.search

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.manager.PendingTorrentIntent
import dev.yashgarg.qbit.ui.common.StatusViewModel
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import qbittorrent.QBittorrentException
import qbittorrent.SEARCH_PLUGINS_ENABLED
import qbittorrent.models.SearchResult

@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    private val pendingTorrent: PendingTorrentIntent,
    application: Application,
) : StatusViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var jobId: Int? = null

    /** Reloads the server's plugins; the screen calls it on every resume, not just the first. */
    fun loadPlugins() {
        viewModelScope.launch {
            repository
                .getSearchPlugins()
                .onOk { plugins ->
                    _uiState.update { current ->
                        val next =
                            current.copy(
                                plugins = plugins,
                                pluginsLoaded = true,
                                // A plugin removed or disabled elsewhere can't stay selected.
                                selectedPlugin =
                                    current.selectedPlugin?.takeIf { name ->
                                        plugins.any { p -> p.name == name && p.enabled }
                                    },
                                // An earlier failure to load plugins no longer applies.
                                error =
                                    current.error.takeUnless { current.phase == SearchPhase.IDLE },
                            )
                        // A category the remaining plugins no longer offer can't stay selected.
                        next.copy(
                            category =
                                next.category.takeIf { id ->
                                    id == SearchState.CATEGORY_ALL ||
                                        next.categories.any { it.first == id }
                                } ?: SearchState.CATEGORY_ALL,
                        )
                    }
                }
                .onErr { error ->
                    _uiState.update {
                        it.copy(
                            pluginsLoaded = true,
                            error =
                                error.friendlyMessage(
                                    getString(CommonR.string.status_search_plugins_load_failure),
                                ),
                        )
                    }
                }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setSort(sort: SearchSort) = _uiState.update { it.copy(sort = sort) }

    fun selectPlugin(name: String?) = _uiState.update {
        it.copy(selectedPlugin = name, category = SearchState.CATEGORY_ALL)
    }

    fun selectCategory(id: String) = _uiState.update { it.copy(category = id) }

    fun search() {
        val state = _uiState.value
        val pattern = state.query.trim()
        if (pattern.isEmpty()) return
        val previousJob = searchJob
        searchJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            // Joined above, so the previous job's id is recorded even if it was still starting.
            jobId?.let { discard(it) }
            jobId = null
            _uiState.update {
                it.copy(phase = SearchPhase.RUNNING, results = emptyList(), error = null)
            }
            val plugins = state.selectedPlugin?.let(::listOf) ?: listOf(SEARCH_PLUGINS_ENABLED)
            // The server may create the job even if this is cancelled mid-request, so the response
            // is always taken and its id recorded for whoever cleans up next.
            val started =
                withContext(NonCancellable) {
                    repository.startSearch(pattern, plugins, state.category).onOk { jobId = it }
                }
            started
                .onOk { id -> poll(id) }
                .onErr { error ->
                    _uiState.update {
                        it.copy(phase = SearchPhase.IDLE, error = error.startFailureMessage())
                    }
                }
        }
    }

    fun stop() {
        // Runs as the current job so a later search() cancels it instead of racing it; the join
        // also waits out a search that is still starting, whose id is then known.
        val previousJob = searchJob
        searchJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            jobId?.let { id ->
                repository.stopSearch(id)
                fetchResults(id)
            }
            _uiState.update { it.copy(phase = SearchPhase.FINISHED) }
        }
    }

    /** Hands [result] to the add-torrent flow, which owns validation and the duplicate check. */
    fun addResult(result: SearchResult) = pendingTorrent.offer(result.fileUrl)

    private suspend fun poll(id: Int) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            val running = fetchResults(id) ?: return
            if (!running) break
        }
        _uiState.update { it.copy(phase = SearchPhase.FINISHED) }
    }

    /** Refreshes the visible results; returns whether the job is still running, null on failure. */
    private suspend fun fetchResults(id: Int): Boolean? {
        var running: Boolean? = null
        val result = repository.getSearchResults(id, limit = MAX_RESULTS)
        // A search started or stopped meanwhile owns the state now; don't overwrite it.
        currentCoroutineContext().ensureActive()
        result
            .onOk { page ->
                var capped = false
                if (page.isRunning && page.total >= MAX_RESULTS) {
                    // Enough to sort through; don't keep the server crawling for the rest.
                    repository.stopSearch(id)
                    capped = true
                }
                running = page.isRunning && !capped
                _uiState.update { it.copy(results = page.results) }
            }
            .onErr { error ->
                _uiState.update {
                    it.copy(
                        phase = SearchPhase.FINISHED,
                        error =
                            error.friendlyMessage(getString(CommonR.string.status_search_failure)),
                    )
                }
            }
        return running
    }

    private suspend fun discard(id: Int) {
        // Best effort: the job may already be gone, and this also runs while the scope is closing.
        withContext(NonCancellable) {
            repository.stopSearch(id)
            repository.deleteSearch(id)
        }
    }

    private fun Throwable.startFailureMessage(): String =
        if (this is QBittorrentException && response?.status == HttpStatusCode.Conflict)
            getString(CommonR.string.error_search_too_many)
        else friendlyMessage(getString(CommonR.string.status_search_start_failure))

    override fun onCleared() {
        val job = searchJob
        // viewModelScope is already cancelled here; a detached best-effort cleanup is needed so the
        // server doesn't hold the finished job (it keeps only a handful at once). Joining first
        // lets a search that was still starting record its id.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            job?.join()
            jobId?.let { discard(it) }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_500L
        const val MAX_RESULTS = 500
    }
}
