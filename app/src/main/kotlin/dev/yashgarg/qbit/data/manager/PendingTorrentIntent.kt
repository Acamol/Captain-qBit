package dev.yashgarg.qbit.data.manager

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hands a torrent-file/magnet URI from an external VIEW intent to whichever screen is ready to add
 * it. `MainActivity` reads the URI directly off the `Intent` it's given (`onCreate`/`onNewIntent`)
 * and offers it here rather than the screen re-reading `Activity#getIntent()` later — a [StateFlow]
 * always replays its latest value to a new collector, so it can't matter whether `MainActivity` or
 * the screen's collector starts first.
 */
@Singleton
class PendingTorrentIntent @Inject constructor() {
    private val _uri = MutableStateFlow<String?>(null)
    val uri: StateFlow<String?> = _uri

    fun offer(uri: String) {
        _uri.value = uri
    }

    fun consume() {
        _uri.value = null
    }
}
