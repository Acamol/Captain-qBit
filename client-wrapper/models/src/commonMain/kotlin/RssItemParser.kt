package qbittorrent.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val articleJson = Json { ignoreUnknownKeys = true }

/**
 * Walks the nested JSON tree returned by `/api/v2/rss/items?withData=true`. A node is a feed iff it
 * has a `url` key; anything else is a folder of further nodes. There's no depth limit and no
 * discriminator field, so this can't be modeled as a single `@Serializable` hierarchy - hence the
 * manual walk (in the same spirit as [KeyMergingTransformer]).
 */
fun parseRssTree(root: JsonObject, parentPath: String = ""): List<RssItem> {
    return root.entries
        .map { (name, element) ->
            val obj = element.jsonObject
            val path = if (parentPath.isEmpty()) name else "$parentPath\\$name"
            if ("url" in obj) {
                RssFeed(
                    name = name,
                    path = path,
                    uid = obj["uid"]?.jsonPrimitive?.content ?: "",
                    url = obj["url"]?.jsonPrimitive?.content ?: "",
                    title = obj["title"]?.jsonPrimitive?.content ?: name,
                    lastBuildDate = obj["lastBuildDate"]?.jsonPrimitive?.content,
                    isLoading = obj["isLoading"]?.jsonPrimitive?.booleanOrNull ?: false,
                    hasError = obj["hasError"]?.jsonPrimitive?.booleanOrNull ?: false,
                    articles =
                        obj["articles"]?.jsonArray?.map {
                            articleJson.decodeFromJsonElement<RssArticle>(it)
                        } ?: emptyList(),
                )
            } else {
                RssFolder(name = name, path = path, children = parseRssTree(obj, path))
            }
        }
        // The server has no defined ordering for its RSS tree keys (insertion order in practice) -
        // sort alphabetically, case-insensitively, folders and feeds interleaved, like the desktop
        // client does.
        .sortedBy { it.name.lowercase() }
}
