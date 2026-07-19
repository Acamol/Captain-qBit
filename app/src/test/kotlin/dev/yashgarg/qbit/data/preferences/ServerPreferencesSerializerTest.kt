package dev.yashgarg.qbit.data.preferences

import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.data.models.ServerViewPrefs
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPreferencesSerializerTest {

    /**
     * An install from before 1.0.0 stored the now-removed global filter/sort keys. Decoding must
     * ignore them rather than throw CorruptionException — which would wipe every saved preference
     * (active server, theme, category colours, …).
     */
    @Test
    fun `decodes prefs that still carry the removed legacy keys`() = runTest {
        val legacyJson =
            """
            {
              "addTorrentCategory": "linux",
              "sortOptionName": "SIZE",
              "sortDirectionAsc": false,
              "filterStateName": "DOWNLOADING",
              "filterCategory": "distros",
              "filterTracker": "https://tracker.example",
              "filterTags": ["hd"],
              "filterUntagged": true,
              "activeServerId": 3,
              "themeMode": 1,
              "lastSeenVersionCode": 7
            }
            """
                .trimIndent()

        val prefs = ServerPreferencesSerializer.readFrom(legacyJson.byteInputStream())

        // Kept fields survive; the removed keys are silently dropped (no crash).
        assertEquals("linux", prefs.addTorrentCategory)
        assertEquals(3, prefs.activeServerId)
        assertEquals(1, prefs.themeMode)
        assertEquals(7, prefs.lastSeenVersionCode)
    }

    /** A written prefs file reads back equal and no longer carries the removed keys. */
    @Test
    fun `round-trips current prefs without the legacy keys`() = runTest {
        val original =
            ServerPreferences(
                activeServerId = 5,
                serverViewPrefs = mapOf(5 to ServerViewPrefs(sortOptionName = "RATIO")),
                themeMode = 2,
            )

        val out = ByteArrayOutputStream()
        ServerPreferencesSerializer.writeTo(original, out)
        val restored = ServerPreferencesSerializer.readFrom(out.toByteArray().inputStream())

        assertEquals(original, restored)
    }
}
