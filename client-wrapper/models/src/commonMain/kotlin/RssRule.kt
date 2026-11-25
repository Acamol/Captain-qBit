package qbittorrent.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * An RSS auto-downloading rule. Mirrors the subset of qBittorrent's rule definition exposed by its
 * own rule editor: match text (raw, same semantics as the desktop client - not parsed client-side),
 * an optional paused/started override, and where matched torrents are filed.
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RssRuleSerializer::class)
data class RssRule(
    val enabled: Boolean = true,
    val mustContain: String = "",
    val mustNotContain: String = "",
    val useRegex: Boolean = false,
    val episodeFilter: String = "",
    val smartFilter: Boolean = false,
    val ignoreDays: Int = 0,
    val addPaused: Boolean? = null,
    val assignedCategory: String = "",
    val savePath: String = "",
    val torrentContentLayout: String? = null,
    val affectedFeeds: List<String> = emptyList(),
    @Transient internal val original: JsonObject? = null,
)

// setRule replaces the entire definition. Never reconstruct a fetched rule from just the fields
// our editor knows about: even its episode history and last-match timestamp are part of this POST.
@OptIn(ExperimentalSerializationApi::class)
internal object RssRuleSerializer : KSerializer<RssRule> {
    override val descriptor = JsonObject.serializer().descriptor
    private val fields = RssRule.generatedSerializer()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val torrentKeys =
        mapOf(
            "addPaused" to "stopped",
            "assignedCategory" to "category",
            "savePath" to "save_path",
            "torrentContentLayout" to "content_layout",
        )

    private fun readFields(definition: JsonObject): RssRule {
        val flat = definition.toMutableMap()
        val params = definition["torrentParams"] as? JsonObject
        if (params != null) {
            // qBittorrent uses the nested object *instead of* the legacy keys, even when an
            // optional nested key is absent (use global default).
            for ((legacy, nested) in torrentKeys) {
                flat.remove(legacy)
                params[nested]?.let { flat[legacy] = it }
            }
        } else if ("torrentContentLayout" !in flat) {
            (flat["createSubfolder"] as? JsonPrimitive)?.booleanOrNull?.let {
                flat["torrentContentLayout"] = JsonPrimitive(if (it) "Original" else "NoSubfolder")
            }
        }
        (flat["affectedFeeds"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.let {
                flat["affectedFeeds"] = JsonArray(listOf(it))
            }
        return json.decodeFromJsonElement(fields, JsonObject(flat))
    }

    override fun deserialize(decoder: Decoder): RssRule {
        val definition = JsonObject.serializer().deserialize(decoder)
        return readFields(definition).copy(original = definition)
    }

    override fun serialize(encoder: Encoder, value: RssRule) {
        JsonObject.serializer().serialize(encoder, definition(value))
    }

    fun definition(value: RssRule, base: JsonObject? = value.original): JsonObject {
        val edited = json.encodeToJsonElement(fields, value).jsonObject
        val original = value.original ?: return edited
        val before = json.encodeToJsonElement(fields, readFields(original)).jsonObject
        val result = (base ?: original).toMutableMap()
        val params = (result["torrentParams"] as? JsonObject)?.toMutableMap()
        for ((key, edit) in edited) {
            if (before[key] == edit) continue
            result[key] = edit
            if (key == "affectedFeeds" && base != null && base[key] != original[key]) {
                // Preserve assignments added by another client that were never in the picker.
                val prior = before.getValue(key) as JsonArray
                val selected = edit as JsonArray
                val current = readFields(base).affectedFeeds.map(::JsonPrimitive)
                result[key] =
                    JsonArray(
                        current.filter { it !in prior || it in selected } +
                            selected.filter { it !in prior && it !in current },
                    )
            }
            val nested = torrentKeys[key]
            if (params != null && nested != null) {
                if (edit == JsonNull) params.remove(nested) else params[nested] = edit
            }
            if (key == "torrentContentLayout" && "createSubfolder" in result) {
                result["createSubfolder"] =
                    if (edit == JsonNull) JsonNull
                    else JsonPrimitive(edit != JsonPrimitive("NoSubfolder"))
            }
        }
        if (params != null) result["torrentParams"] = JsonObject(params)
        return JsonObject(result)
    }
}

/** Apply only the user's edits to the latest server definition. Never use a new-rule template. */
fun RssRule.applyEditsTo(latest: RssRule): RssRule? {
    if (original == null || latest.original == null) return null
    if (latest.original == original) return this
    val merged = RssRuleSerializer.definition(this, latest.original)
    return Json.decodeFromJsonElement(RssRule.serializer(), merged)
}
