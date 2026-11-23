package dev.yashgarg.qbit.utils

import java.net.HttpURLConnection
import java.net.URL

/** Best-effort fetch of a `.torrent` file from an http(s) link; null on any failure. */
object TorrentDownloader {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 5 * 1024 * 1024

    fun fetch(url: String): ByteArray? {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                if (conn.contentLengthLong > MAX_BYTES) return null
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) return null
                    }
                }
                out.toByteArray()
            } finally {
                conn.disconnect()
            }
        } catch (e: java.io.IOException) {
            null
        }
    }
}
