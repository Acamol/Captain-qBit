package dev.yashgarg.qbit.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Best-effort fetch of a `.torrent` file from an http(s) link; null on any failure.
 *
 * Redirects are followed manually (up to [MAX_REDIRECTS]) so a public link can't bounce the phone
 * to a private/loopback address. A link that is itself on a private address (a LAN indexer, a VPN)
 * may redirect freely, so legitimate setups keep working.
 */
object TorrentDownloader {
    private const val TIMEOUT_MS = 10_000
    private const val TOTAL_TIMEOUT_MS = 30_000L
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    fun fetch(url: String): ByteArray? {
        val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MS
        return try {
            var current = URL(url)
            if (!isHttp(current)) return null
            val startedPrivate = isPrivateHost(current.host)
            repeat(MAX_REDIRECTS + 1) {
                val conn = current.openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    val code = conn.responseCode
                    if (code in REDIRECT_CODES) {
                        val location = conn.getHeaderField("Location") ?: return null
                        current = URL(current, location)
                        if (!isHttp(current)) return null
                        if (!startedPrivate && isPrivateHost(current.host)) return null
                        return@repeat
                    }
                    if (code != HttpURLConnection.HTTP_OK) return null
                    return read(conn, deadline)
                } finally {
                    conn.disconnect()
                }
            }
            null
        } catch (e: IOException) {
            null
        }
    }

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    private fun isHttp(url: URL) =
        url.protocol.equals("http", true) || url.protocol.equals("https", true)

    private fun read(conn: HttpURLConnection, deadline: Long): ByteArray? {
        if (conn.contentLengthLong > MAX_BYTES) return null
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        conn.inputStream.use { input ->
            while (true) {
                if (System.currentTimeMillis() > deadline) return null
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) return null
            }
        }
        return out.toByteArray()
    }

    // Loopback, LAN, link-local and CGNAT (100.64/10, used by Tailscale) all count as private.
    private fun isPrivateHost(host: String): Boolean =
        InetAddress.getAllByName(host).any {
            it.isAnyLocalAddress ||
                it.isLoopbackAddress ||
                it.isSiteLocalAddress ||
                it.isLinkLocalAddress ||
                it.address.let { b ->
                    b.size == 4 && b[0].toInt() == 100 && (b[1].toInt() and 0xC0) == 0x40
                }
        }
}
