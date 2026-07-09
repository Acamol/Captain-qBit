package qbittorrent

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import qbittorrent.internal.bodyOrThrow
import qbittorrent.internal.orThrow
import qbittorrent.models.Category

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.setTorrentCategory(
    hashes: List<String> = QBittorrentClient.allList,
    category: String
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/setCategory",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("category", category)
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getCategories(): List<Category> {
    return http
        .get("${config.baseUrl}/api/v2/torrents/categories")
        .bodyOrThrow<Map<String, Category>>()
        .values
        .toList()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.createCategory(name: String, savePath: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/createCategory",
            formParameters =
                Parameters.build {
                    append("category", name)
                    append("savePath", savePath)
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.editCategory(name: String, savePath: String) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/editCategory",
            formParameters =
                Parameters.build {
                    append("category", name)
                    append("savePath", savePath)
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeCategories(names: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/removeCategories",
            formParameters = Parameters.build { append("categories", names.joinToString("\n")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.addTorrentTags(
    hashes: List<String> = QBittorrentClient.allList,
    tags: List<String>
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/addTags",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("tags", tags.joinToString(","))
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.removeTorrentTags(
    hashes: List<String> = QBittorrentClient.allList,
    tags: List<String>
) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/removeTags",
            formParameters =
                Parameters.build {
                    append("hashes", hashes.joinToString("|"))
                    append("tags", tags.joinToString(","))
                }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.getTags(): List<String> =
    http.get("${config.baseUrl}/api/v2/torrents/tags").bodyOrThrow()

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.createTags(tags: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/createTags",
            formParameters = Parameters.build { append("tags", tags.joinToString(",")) }
        )
        .orThrow()
}

@Throws(QBittorrentException::class, CancellationException::class)
suspend fun QBittorrentClient.deleteTags(tags: List<String>) {
    http
        .submitForm(
            "${config.baseUrl}/api/v2/torrents/deleteTags",
            formParameters = Parameters.build { append("tags", tags.joinToString(",")) }
        )
        .orThrow()
}
