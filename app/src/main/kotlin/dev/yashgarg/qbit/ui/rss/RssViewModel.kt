package dev.yashgarg.qbit.ui.rss

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.ui.common.StatusViewModel
import dev.yashgarg.qbit.ui.navigation.Routes
import dev.yashgarg.qbit.utils.friendlyMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qbittorrent.models.RssRule

/**
 * Shared across the Feeds/Rules screen, the per-feed articles screen, and the rule editor - each
 * gets its own instance (one per nav back-stack entry) but all load the same items/rules, so
 * [itemPath]/[ruleName] (from this entry's [SavedStateHandle], present only on the latter two
 * screens' routes) just pick out which node/rule this particular screen is showing.
 */
@HiltViewModel
class RssViewModel
@Inject
constructor(
    private val repository: QbitRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
) : StatusViewModel(context) {
    private val _uiState = MutableStateFlow(RssState())
    val uiState = _uiState.asStateFlow()

    val itemPath: String? = savedStateHandle.get<String>(Routes.ARG_RSS_ITEM_PATH)
    val ruleName: String? =
        savedStateHandle.get<String>(Routes.ARG_RSS_RULE_NAME)?.takeIf { it.isNotBlank() }

    init {
        load(refresh = false)
        viewModelScope.launch {
            repository.getRssRefreshInterval().onOk { minutes ->
                _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
            }
        }
        // Categories/tags for the rule editor's pickers, and already-added torrent hashes for the
        // articles screen's "already added" indicator - same source TorrentDetailsViewModel uses.
        viewModelScope.launch {
            repository
                .observeMainData()
                .catch { /* non-fatal, ignore */ }
                .collectLatest { mainData ->
                    _uiState.update {
                        it.copy(
                            availableCategories = mainData.categories.keys.sorted(),
                            availableTags = mainData.tags.sorted(),
                            existingTorrentHashes =
                                mainData.torrents.keys.map(String::lowercase).toSet(),
                        )
                    }
                }
        }
    }

    fun refresh() = load(refresh = true)

    fun toggleSort() {
        _uiState.update { it.copy(sortDescending = !it.sortDescending) }
    }

    fun setRefreshInterval(minutes: Int) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_interval_updated),
            failureMessage = getString(CommonR.string.status_rss_interval_update_failure),
            onSuccess = { _uiState.update { it.copy(refreshIntervalMinutes = minutes) } },
        ) {
            repository.setRssRefreshInterval(minutes)
        }
    }

    private fun load(refresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
            repository
                .getRssItems()
                .onOk { items -> _uiState.update { it.copy(items = items) } }
                .onErr { error ->
                    _uiState.update {
                        it.copy(
                            error =
                                error.friendlyMessage(
                                    getString(CommonR.string.status_rss_load_failure)
                                )
                        )
                    }
                }
            repository.getRssRules().onOk { rules -> _uiState.update { it.copy(rules = rules) } }
            _uiState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun addFolder(path: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_folder_added),
            failureMessage = getString(CommonR.string.status_rss_add_folder_failure),
            onSuccess = { refresh() },
        ) {
            repository.addRssFolder(path)
        }
    }

    fun addFeed(url: String, path: String? = null) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_feed_added),
            failureMessage = getString(CommonR.string.status_rss_add_feed_failure),
            onSuccess = { refresh() },
        ) {
            repository.addRssFeed(url, path)
        }
    }

    fun removeItem(itemPath: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_item_removed),
            failureMessage = getString(CommonR.string.status_rss_remove_item_failure),
            onSuccess = { refresh() },
        ) {
            repository.removeRssItem(itemPath)
        }
    }

    /** No success toast - drag-to-move already shows the item in its new spot. */
    fun moveItem(itemPath: String, destPath: String) {
        viewModelScope.launch {
            repository
                .moveRssItem(itemPath, destPath)
                .onOk { refresh() }
                .onErr {
                    emitStatus(
                        it.friendlyMessage(getString(CommonR.string.status_rss_move_item_failure))
                    )
                }
        }
    }

    /** [articleId] null marks every article in the feed/folder as read. */
    fun markAsRead(itemPath: String, articleId: String? = null) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_marked_read),
            failureMessage = getString(CommonR.string.status_rss_mark_read_failure),
            onSuccess = { refresh() },
        ) {
            repository.markRssItemAsRead(itemPath, articleId)
        }
    }

    fun refreshItem(itemPath: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_refreshing_item),
            failureMessage = getString(CommonR.string.status_rss_refresh_item_failure),
            onSuccess = { refresh() },
        ) {
            repository.refreshRssItem(itemPath)
        }
    }

    fun setRule(ruleName: String, rule: RssRule) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_rule_saved),
            failureMessage = getString(CommonR.string.status_rss_save_rule_failure),
            onSuccess = { refresh() },
        ) {
            repository.setRssRule(ruleName, rule)
        }
    }

    fun renameRule(ruleName: String, newRuleName: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_rule_renamed),
            failureMessage = getString(CommonR.string.status_rss_rename_rule_failure),
            onSuccess = { refresh() },
        ) {
            repository.renameRssRule(ruleName, newRuleName)
        }
    }

    fun removeRule(ruleName: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_rule_removed),
            failureMessage = getString(CommonR.string.status_rss_remove_rule_failure),
            onSuccess = { refresh() },
        ) {
            repository.removeRssRule(ruleName)
        }
    }

    fun loadMatchingArticles(ruleName: String, onResult: (Map<String, List<String>>) -> Unit) {
        viewModelScope.launch {
            repository.getRssMatchingArticles(ruleName).onOk(onResult).onErr {
                emitStatus(it.friendlyMessage())
            }
        }
    }

    /** Adds a torrent straight from an article's enclosure link, using server defaults. */
    fun addTorrentFromArticle(torrentUrl: String) {
        launchStatus(
            successMessage = getString(CommonR.string.status_rss_torrent_added),
            failureMessage = getString(CommonR.string.status_rss_add_torrent_failure),
        ) {
            repository.addTorrentUrl(torrentUrl)
        }
    }
}
