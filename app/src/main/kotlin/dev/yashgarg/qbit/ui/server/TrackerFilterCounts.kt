package dev.yashgarg.qbit.ui.server

import androidx.core.net.toUri

internal fun trackerFilterCounts(
    urls: Iterable<String>,
    parseHost: (String) -> String? = { it.toUri().host },
): Map<String, Int> {
    val parsedHosts = mutableMapOf<String, String>()
    val hosts = mutableSetOf<String>()
    val counts = mutableMapOf<String, Int>()
    for (url in urls) {
        if (url.isBlank()) continue
        val host = parsedHosts.getOrPut(url) { parseHost(url).orEmpty() }
        if (host.isBlank()) continue
        hosts.add(host)
        val key = host.lowercase()
        counts[key] = (counts[key] ?: 0) + 1
    }
    return hosts.sorted().associateWith { counts.getValue(it.lowercase()) }
}
