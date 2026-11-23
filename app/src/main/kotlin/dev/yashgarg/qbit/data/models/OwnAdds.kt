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

    // A plain http(s) link to a .torrent has no hash the app can know up front, so it is recorded
    // as an anonymous "slot" that suppresses the next new torrent on that server. Slots expire much
    // sooner, to bound how long an add that never materialises could swallow an unrelated alert.
    const val SLOT_TTL_MS = 5 * 60 * 1000L

    fun key(serverId: Int, hash: String) = "$serverId|${hash.lowercase()}"

    fun slotKey(serverId: Int, now: Long) = "$serverId|?$now-${System.nanoTime()}"

    private fun String.isSlot() = substringAfter('|').startsWith('?')

    fun Map<String, Long>.pruned(now: Long): Map<String, Long> = filter { (k, v) ->
        now - v < if (k.isSlot()) SLOT_TTL_MS else TTL_MS
    }

    /** The oldest unused slot for [serverId], if any. */
    fun Map<String, Long>.oldestSlot(serverId: Int): String? =
        entries.filter { it.key.startsWith("$serverId|?") }.minByOrNull { it.value }?.key

    fun Map<String, Long>.registered(key: String, now: Long): Map<String, Long> =
        (pruned(now) + (key to now))
            .entries
            .sortedBy { it.value }
            .takeLast(MAX_ENTRIES)
            .associate {
                it.key to it.value
            }
}
