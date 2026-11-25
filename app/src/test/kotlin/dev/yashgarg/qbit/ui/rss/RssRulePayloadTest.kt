package dev.yashgarg.qbit.ui.rss

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import qbittorrent.models.RssRule
import qbittorrent.models.applyEditsTo

class RssRulePayloadTest {
    // rss/rules uses null legacy aliases for inherited values; serializeAddTorrentParams omits
    // their nested counterparts. The server accepts setRule as a replacement, not a patch.
    private val rules =
        """
        {
          "Weekly": {
            "enabled": true, "priority": 2, "mustContain": "weekly", "mustNotContain": "",
            "useRegex": false, "episodeFilter": "1x1-;", "smartFilter": true, "ignoreDays": 3,
            "previouslyMatchedEpisodes": ["1x01", "1x02-REPACK"],
            "lastMatch": "Fri, 20 Nov 2026 17:00:00 +0000",
            "affectedFeeds": ["https://example.org/news.xml"],
            "addPaused": null, "torrentContentLayout": null, "assignedCategory": "news",
            "savePath": "/data/news",
            "torrentParams": {
              "save_path": "/data/news", "category": "news", "tags": ["weekly"],
              "use_auto_tmm": false, "operating_mode": "AutoManaged", "download_limit": -1,
              "download_path": "", "skip_checking": false, "ratio_limit": -2,
              "future_option": {"enabled":true}
            },
            "future_rule_option": [null, 42]
          }
        }
        """
            .trimIndent()
    private val definition = Json.parseToJsonElement(rules).jsonObject.getValue("Weekly").jsonObject

    private fun wire(rule: RssRule) =
        Json.encodeToJsonElement(RssRule.serializer(), rule).jsonObject

    private fun read(raw: JsonObject) = Json.decodeFromJsonElement(RssRule.serializer(), raw)

    @Test
    fun `toggling an inherited rule changes only enabled in the posted definition`() {
        val rule = Json.decodeFromString<Map<String, RssRule>>(rules).getValue("Weekly")
        assertNull(rule.torrentContentLayout)
        assertEquals(
            definition + ("enabled" to JsonPrimitive(false)),
            wire(rule.copy(enabled = false)),
        )
    }

    @Test
    fun `edits use the authoritative nested options and update both representations`() {
        val params = definition.getValue("torrentParams").jsonObject
        val raw =
            JsonObject(
                definition +
                    ("torrentParams" to
                        JsonObject(
                            params +
                                mapOf(
                                    "category" to JsonPrimitive("nested"),
                                    "save_path" to JsonPrimitive("/nested"),
                                    "stopped" to JsonPrimitive(true),
                                    "content_layout" to JsonPrimitive("Subfolder"),
                                ),
                        )),
            )
        val rule = read(raw)
        assertEquals("nested", rule.assignedCategory)
        assertEquals("/nested", rule.savePath)
        assertEquals(true, rule.addPaused)
        assertEquals("Subfolder", rule.torrentContentLayout)

        val edited =
            wire(
                rule.copy(
                    assignedCategory = "series",
                    savePath = "/series",
                    addPaused = null,
                    torrentContentLayout = null,
                ),
            )
        val nested = edited.getValue("torrentParams").jsonObject
        assertEquals(JsonPrimitive("series"), edited["assignedCategory"])
        assertEquals(JsonPrimitive("/series"), edited["savePath"])
        assertEquals(JsonNull, edited["addPaused"])
        assertEquals(JsonNull, edited["torrentContentLayout"])
        assertEquals(
            params +
                mapOf(
                    "category" to JsonPrimitive("series"),
                    "save_path" to JsonPrimitive("/series"),
                ),
            nested,
        )

        val explicit = wire(read(edited).copy(addPaused = false, torrentContentLayout = "Original"))
        assertEquals(JsonPrimitive(false), explicit.getValue("torrentParams").jsonObject["stopped"])
        assertEquals(
            JsonPrimitive("Original"),
            explicit.getValue("torrentParams").jsonObject["content_layout"],
        )
    }

    @Test
    fun `editing a legacy rule keeps its wire shape and match history`() {
        val raw = JsonObject(definition - "torrentParams")
        val rule = read(raw)
        assertNull(rule.torrentContentLayout)
        val edited =
            wire(
                rule.copy(
                    savePath = "/new",
                    torrentContentLayout = "NoSubfolder",
                    addPaused = false,
                ),
            )
        assertEquals(
            raw +
                mapOf(
                    "savePath" to JsonPrimitive("/new"),
                    "torrentContentLayout" to JsonPrimitive("NoSubfolder"),
                    "addPaused" to JsonPrimitive(false),
                ),
            edited,
        )
        assertFalse("torrentParams" in edited)
    }

    @Test
    fun `saving a draft after refresh keeps new server history and unrelated changes`() {
        val draft = read(definition).copy(mustContain = "1080p", affectedFeeds = emptyList())
        val otherFeed = JsonPrimitive("https://example.org/added-by-another-client.xml")
        val refreshed =
            JsonObject(
                definition +
                    mapOf(
                        "lastMatch" to JsonPrimitive("Mon, 23 Nov 2026 19:00:00 +0000"),
                        "ignoreDays" to JsonPrimitive(5),
                        "affectedFeeds" to
                            JsonArray(
                                listOf(JsonPrimitive("https://example.org/news.xml"), otherFeed),
                            ),
                    ),
            )
        assertEquals(
            refreshed +
                mapOf(
                    "mustContain" to JsonPrimitive("1080p"),
                    "affectedFeeds" to JsonArray(listOf(otherFeed)),
                ),
            wire(requireNotNull(draft.applyEditsTo(read(refreshed)))),
        )
    }
}
