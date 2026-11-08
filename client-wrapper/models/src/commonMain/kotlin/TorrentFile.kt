package qbittorrent.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TorrentFile(
    /**
     * The index of this file in the torrent contents list. Older servers don't send it, so it's -1
     * until [withIndexes] fills it from the file's position.
     */
    val index: Int = -1,
    /** File name (including relative path) */
    val name: String,
    /** File size (bytes) */
    val size: Long,
    /** File progress (percentage/100) */
    val progress: Float,
    /** File priority */
    val priority: Int,
    /** True if file is seeding/complete */
    @SerialName("is_seed") val isSeeding: Boolean? = false,
    /**
     * The first number is the starting piece index and the second number is the ending piece index
     * (inclusive)
     */
    @SerialName("piece_range") val pieceRange: List<Int>,
    /** Percentage of file pieces currently available */
    val availability: Float,
)

/** Fills in [TorrentFile.index] from each file's position where the server didn't send it. */
fun List<TorrentFile>.withIndexes(): List<TorrentFile> = mapIndexed { position, file ->
    if (file.index >= 0) file else file.copy(index = position)
}
