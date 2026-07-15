package dev.yashgarg.qbit.utils

import java.security.MessageDigest

/**
 * Minimal bencode parser for `.torrent` files: extracts the torrent name, its file list, and the v1
 * infohash. This is what lets the add flow show the file tree *before* anything is sent to the
 * server, and lets it find the torrent again after adding (the add API returns no hash).
 *
 * Parsing is defensive: any structural surprise returns null and the caller falls back to a plain
 * add. v2-only torrents (no v1 `files`/`length` in the info dict) also return null.
 */
object TorrentFileParser {

    data class ParsedTorrent(
        /** v1 infohash, lowercase hex. */
        val hash: String,
        val name: String,
        /** Paths as qBittorrent reports them: prefixed with the root folder for multi-file. */
        val files: List<ParsedFile>,
    )

    data class ParsedFile(val path: String, val size: Long)

    fun parse(bytes: ByteArray): ParsedTorrent? {
        return try {
            val decoder = Bencode(bytes)
            val root = decoder.decode() as? Map<*, *> ?: return null
            val infoRange = decoder.infoRange ?: return null
            val info = root["info"] as? Map<*, *> ?: return null

            val name = (info["name"] as? ByteArray)?.decodeToString() ?: return null

            val files =
                when (val fileList = info["files"]) {
                    // Multi-file: paths are "<root>/<segments...>", matching the server's naming.
                    is List<*> ->
                        fileList.map { entry ->
                            val dict = entry as? Map<*, *> ?: return null
                            val size = dict["length"] as? Long ?: return null
                            val segments =
                                (dict["path"] as? List<*>)?.map {
                                    (it as? ByteArray)?.decodeToString() ?: return null
                                } ?: return null
                            ParsedFile("$name/${segments.joinToString("/")}", size)
                        }
                    // Single-file: the name is the file.
                    else -> {
                        val size = info["length"] as? Long ?: return null
                        listOf(ParsedFile(name, size))
                    }
                }
            if (files.isEmpty()) return null

            // BitTorrent v1 infohash: the protocol defines it as the SHA-1 of the bencoded info
            // dict. It's an interop identifier mandated by the protocol, not a security control.
            val digest =
                MessageDigest.getInstance("SHA-1")
                    .digest(bytes.copyOfRange(infoRange.first, infoRange.last + 1))
            val hash = digest.joinToString("") { "%02x".format(it) }

            ParsedTorrent(hash, name, files)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the v1 infohash from a magnet link (`xt=urn:btih:<40-hex or 32-base32>`), lowercase
     * hex, or null when there isn't one (e.g. v2-only magnets or plain http links).
     */
    fun magnetHash(url: String): String? {
        val match = MAGNET_BTIH.find(url) ?: return null
        val value = match.groupValues[1]
        return when (value.length) {
            40 -> value.lowercase().takeIf { it.all { c -> c in "0123456789abcdef" } }
            32 -> base32ToHex(value.uppercase())
            else -> null
        }
    }

    private val MAGNET_BTIH = Regex("""urn:btih:([A-Za-z0-9]+)""")

    private fun base32ToHex(value: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var buffer = 0L
        val out = StringBuilder()
        for (c in value) {
            val idx = alphabet.indexOf(c)
            if (idx == -1) return null
            buffer = (buffer shl 5) or idx.toLong()
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.append("%02x".format((buffer shr bits) and 0xFF))
            }
        }
        return out.toString().takeIf { it.length == 40 }
    }

    /** Bencode decoder that records the byte range of the top-level `info` value. */
    private class Bencode(private val bytes: ByteArray) {
        private var pos = 0

        /** Start/end (inclusive) byte offsets of the root dict's `info` value. */
        var infoRange: IntRange? = null
            private set

        fun decode(): Any = decodeValue(isRoot = true)

        private fun decodeValue(isRoot: Boolean = false): Any =
            when (bytes[pos].toInt().toChar()) {
                'd' -> decodeDict(isRoot)
                'l' -> decodeList()
                'i' -> decodeInt()
                else -> decodeString()
            }

        private fun decodeDict(isRoot: Boolean): Map<String, Any> {
            pos++ // 'd'
            val map = mutableMapOf<String, Any>()
            while (bytes[pos].toInt().toChar() != 'e') {
                val key = decodeString().decodeToString()
                val start = pos
                val value = decodeValue()
                if (isRoot && key == "info") infoRange = start until pos
                map[key] = value
            }
            pos++ // 'e'
            return map
        }

        private fun decodeList(): List<Any> {
            pos++ // 'l'
            val list = mutableListOf<Any>()
            while (bytes[pos].toInt().toChar() != 'e') list.add(decodeValue())
            pos++ // 'e'
            return list
        }

        private fun decodeInt(): Long {
            pos++ // 'i'
            val end = indexOf('e')
            val value = String(bytes, pos, end - pos).toLong()
            pos = end + 1
            return value
        }

        private fun decodeString(): ByteArray {
            val colon = indexOf(':')
            val length = String(bytes, pos, colon - pos).toInt()
            require(length >= 0 && colon + 1 + length <= bytes.size)
            val value = bytes.copyOfRange(colon + 1, colon + 1 + length)
            pos = colon + 1 + length
            return value
        }

        private fun indexOf(char: Char): Int {
            var i = pos
            while (bytes[i].toInt().toChar() != char) i++
            return i
        }
    }
}
