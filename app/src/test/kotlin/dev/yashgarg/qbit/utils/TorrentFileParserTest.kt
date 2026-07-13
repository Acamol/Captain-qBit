package dev.yashgarg.qbit.utils

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFileParserTest {

    // info value: d6:lengthi42e4:name8:test.isoe
    private val singleFile =
        "d8:announce15:http://tr/annce4:infod6:lengthi42e4:name8:test.isoee".encodeToByteArray()

    // info value: two files under root dir "root": root/sub/a.txt (7 bytes), root/b.bin (9 bytes)
    private val multiFile =
        ("d4:infod5:filesl" +
                "d6:lengthi7e4:pathl3:sub5:a.txtee" +
                "d6:lengthi9e4:pathl5:b.binee" +
                "e4:name4:roote" +
                "e")
            .encodeToByteArray()

    private fun sha1Hex(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.encodeToByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Test
    fun `parses single-file torrent`() {
        val parsed = requireNotNull(TorrentFileParser.parse(singleFile))
        assertEquals("test.iso", parsed.name)
        assertEquals(1, parsed.files.size)
        assertEquals("test.iso", parsed.files[0].path)
        assertEquals(42L, parsed.files[0].size)
        // The infohash must cover exactly the bencoded info value.
        assertEquals(sha1Hex("d6:lengthi42e4:name8:test.isoe"), parsed.hash)
    }

    @Test
    fun `parses multi-file torrent with root-prefixed paths`() {
        val parsed = requireNotNull(TorrentFileParser.parse(multiFile))
        assertEquals("root", parsed.name)
        assertEquals(listOf("root/sub/a.txt", "root/b.bin"), parsed.files.map { it.path })
        assertEquals(listOf(7L, 9L), parsed.files.map { it.size })
        assertEquals(
            sha1Hex(
                "d5:filesl" +
                    "d6:lengthi7e4:pathl3:sub5:a.txtee" +
                    "d6:lengthi9e4:pathl5:b.binee" +
                    "e4:name4:roote"
            ),
            parsed.hash,
        )
    }

    @Test
    fun `garbage returns null`() {
        assertNull(TorrentFileParser.parse("not a torrent".encodeToByteArray()))
        assertNull(TorrentFileParser.parse(ByteArray(0)))
        // Valid bencode but no info dict.
        assertNull(TorrentFileParser.parse("d3:foo3:bare".encodeToByteArray()))
    }

    @Test
    fun `extracts hex magnet hash`() {
        val hash = "c9e15763f722f23e98a29decdfae341b98d53056"
        assertEquals(
            hash,
            TorrentFileParser.magnetHash("magnet:?xt=urn:btih:${hash.uppercase()}&dn=x"),
        )
    }

    @Test
    fun `extracts base32 magnet hash`() {
        // base32("\x00\x01\x02...\x13") for a known round-trip.
        val hex = "000102030405060708090a0b0c0d0e0f10111213"
        val base32 = "AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQT"
        assertEquals(hex, TorrentFileParser.magnetHash("magnet:?xt=urn:btih:$base32"))
    }

    @Test
    fun `non-magnet links return null`() {
        assertNull(TorrentFileParser.magnetHash("https://example.com/file.torrent"))
        assertNull(TorrentFileParser.magnetHash("magnet:?xt=urn:btmh:1220cafe"))
    }
}
