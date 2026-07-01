package dev.yashgarg.qbit.ui.server

import qbittorrent.models.Torrent

enum class SortOption(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    TOTAL_SIZE("Total size"),
    PROGRESS("Progress"),
    DOWNLOAD_SPEED("Download speed"),
    UPLOAD_SPEED("Upload speed"),
    DOWNLOADED("Downloaded"),
    UPLOADED("Uploaded"),
    DOWNLOADED_SESSION("Downloaded (session)"),
    UPLOADED_SESSION("Uploaded (session)"),
    AMOUNT_LEFT("Remaining"),
    ETA("ETA"),
    RATIO("Ratio"),
    AVAILABILITY("Availability"),
    SEEDS("Seeds"),
    CONNECTED_SEEDS("Connected seeds"),
    PEERS("Peers"),
    CONNECTED_PEERS("Connected peers"),
    PRIORITY("Priority"),
    CATEGORY("Category"),
    TRACKER("Tracker"),
    ADDED_ON("Added on"),
    COMPLETED_ON("Completed on"),
    LAST_ACTIVITY("Last activity"),
    TIME_ACTIVE("Time active"),
    SEEDING_TIME("Seeding time"),
}

enum class SortDirection {
    ASC,
    DESC
}

fun List<Torrent>.sortedWith(option: SortOption, direction: SortDirection): List<Torrent> {
    val comparator: Comparator<Torrent> =
        when (option) {
            SortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.SIZE -> compareBy { it.size }
            SortOption.TOTAL_SIZE -> compareBy { it.totalSize }
            SortOption.PROGRESS -> compareBy { it.progress }
            SortOption.DOWNLOAD_SPEED -> compareBy { it.dlspeed }
            SortOption.UPLOAD_SPEED -> compareBy { it.uploadSpeed }
            SortOption.DOWNLOADED -> compareBy { it.downloaded }
            SortOption.UPLOADED -> compareBy { it.uploaded }
            SortOption.DOWNLOADED_SESSION -> compareBy { it.downloadedSession }
            SortOption.UPLOADED_SESSION -> compareBy { it.uploadedSession }
            SortOption.AMOUNT_LEFT -> compareBy { it.amountLeft }
            SortOption.ETA -> compareBy { it.eta }
            SortOption.RATIO -> compareBy { it.ratio }
            SortOption.AVAILABILITY -> compareBy { it.availability }
            SortOption.SEEDS -> compareBy { it.seedsInSwarm }
            SortOption.CONNECTED_SEEDS -> compareBy { it.connectedSeeds }
            SortOption.PEERS -> compareBy { it.leechersInSwarm }
            SortOption.CONNECTED_PEERS -> compareBy { it.connectedLeechers }
            SortOption.PRIORITY -> compareBy { it.priority }
            SortOption.CATEGORY -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.category }
            SortOption.TRACKER -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.tracker }
            SortOption.ADDED_ON -> compareBy { it.addedOn }
            SortOption.COMPLETED_ON -> compareBy { it.completedOn }
            SortOption.LAST_ACTIVITY -> compareBy { it.lastActivity }
            SortOption.TIME_ACTIVE -> compareBy { it.timeActive }
            SortOption.SEEDING_TIME -> compareBy { it.seedingTime }
        }
    return if (direction == SortDirection.ASC) sortedWith(comparator)
    else sortedWith(comparator.reversed())
}
