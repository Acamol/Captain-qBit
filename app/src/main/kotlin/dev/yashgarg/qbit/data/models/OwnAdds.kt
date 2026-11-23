package dev.yashgarg.qbit.data.models

/**
 * Bookkeeping for torrents added from this app, so the "torrent added" alert can skip them. Kept as
 * a small map of `"$serverId|$hash"` to the epoch-millis it was registered at. Memory stays bounded
 * three ways: an entry is dropped as soon as the worker sees (and suppresses) its torrent, stale
 * entries expire after [TTL_MS] (an add that failed or was a duplicate never shows up as new), and
 * the map never holds more than [MAX_ENTRIES], evicting the oldest first.
 */
object OwnAdds {
    const val TTL_MS = 60 * 60 * 1000L
    const val MAX_ENTRIES = 50

    fun key(serverId: Int, hash: String) = "$serverId|${hash.lowercase()}"

    fun Map<String, Long>.pruned(now: Long): Map<String, Long> = filterValues { now - it < TTL_MS }

    fun Map<String, Long>.registered(key: String, now: Long): Map<String, Long> =
        (pruned(now) + (key to now))
            .entries
            .sortedBy { it.value }
            .takeLast(MAX_ENTRIES)
            .associate {
                it.key to it.value
            }
}
