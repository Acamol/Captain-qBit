package dev.yashgarg.qbit.utils

import java.security.MessageDigest

/**
 * Computes the v1 info hash of a torrent so we can tell whether it is already present in the client
 * before doing anything else. qBittorrent keys its torrent map by the lowercase hex info hash, so
 * the values returned here are directly comparable to `MainData.torrents.keys`.
 *
 * Only the common v1 (SHA-1) case is handled; v2-only / hybrid torrents hash differently and will
 * simply fall through (returning null), in which case callers should proceed as usual.
 */
object TorrentHashUtil {

    /**
     * SHA-1 info hash (lowercase hex) of a bencoded `.torrent` file, or null if it can't be read.
     */
    fun infoHashFromTorrent(bytes: ByteArray): String? =
        try {
            BencodeReader(bytes).infoValueRange()?.let { (start, end) ->
                MessageDigest.getInstance("SHA-1")
                    .apply { update(bytes, start, end - start) }
                    .digest()
                    .toHex()
            }
        } catch (e: Exception) {
            null
        }

    /** btih info hash (lowercase hex) from a magnet link, or null if absent/unsupported. */
    fun infoHashFromMagnet(uri: String): String? {
        val raw =
            Regex("xt=urn:btih:([^&]+)", RegexOption.IGNORE_CASE).find(uri)?.groupValues?.get(1)
                ?: return null
        return when (raw.length) {
            40 -> raw.lowercase() // hex-encoded v1 hash
            32 -> base32ToHex(raw) // base32-encoded v1 hash
            else -> null
        }
    }

    private fun base32ToHex(input: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = StringBuilder()
        for (c in input.uppercase().trimEnd('=')) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.append("%02x".format((buffer shr bits) and 0xFF))
            }
        }
        return out.toString()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Minimal bencode walker that locates the raw byte range of the top-level `info` value. */
    private class BencodeReader(private val data: ByteArray) {
        private var pos = 0

        fun infoValueRange(): Pair<Int, Int>? {
            pos = 0
            if (!consume('d')) return null
            while (pos < data.size && data[pos] != 'e'.code.toByte()) {
                val key = readByteString()
                val start = pos
                skipValue()
                if (key == "info") return start to pos
            }
            return null
        }

        private fun consume(c: Char): Boolean {
            if (pos < data.size && data[pos] == c.code.toByte()) {
                pos++
                return true
            }
            return false
        }

        private fun readByteString(): String {
            val colon = indexOf(':'.code.toByte(), pos)
            val len = String(data, pos, colon - pos, Charsets.US_ASCII).toInt()
            pos = colon + 1
            val s = String(data, pos, len, Charsets.ISO_8859_1)
            pos += len
            return s
        }

        private fun skipValue() {
            when (data[pos]) {
                'i'.code.toByte() -> { // integer: i<digits>e
                    pos++
                    while (data[pos] != 'e'.code.toByte()) pos++
                    pos++
                }
                'l'.code.toByte(),
                'd'.code.toByte() -> { // list or dict
                    val isDict = data[pos] == 'd'.code.toByte()
                    pos++
                    while (data[pos] != 'e'.code.toByte()) {
                        if (isDict) readByteString()
                        skipValue()
                    }
                    pos++
                }
                else -> { // byte string: <len>:<bytes> (skipped by length, safe for binary)
                    val colon = indexOf(':'.code.toByte(), pos)
                    val len = String(data, pos, colon - pos, Charsets.US_ASCII).toInt()
                    pos = colon + 1 + len
                }
            }
        }

        private fun indexOf(b: Byte, from: Int): Int {
            var i = from
            while (i < data.size && data[i] != b) i++
            return i
        }
    }
}
