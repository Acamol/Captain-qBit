package dev.yashgarg.qbit.ui.server

import android.net.Uri
import qbittorrent.models.Torrent

enum class StateFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    STOPPED("Stopped"),
    COMPLETED("Completed"),
    ERRORED("Errored"),
}

fun Torrent.matchesFilter(filter: StateFilter): Boolean =
    when (filter) {
        StateFilter.ALL -> true
        StateFilter.ACTIVE -> dlspeed > 0 || uploadSpeed > 0
        StateFilter.DOWNLOADING ->
            state in
                setOf(
                    Torrent.State.DOWNLOADING,
                    Torrent.State.FORCED_DL,
                    Torrent.State.META_DL,
                    Torrent.State.FORCED_META_DL,
                    Torrent.State.ALLOCATING,
                    Torrent.State.QUEUED_DL,
                    Torrent.State.STALLED_DL,
                )
        StateFilter.SEEDING ->
            state in
                setOf(
                    Torrent.State.UPLOADING,
                    Torrent.State.FORCED_UP,
                    Torrent.State.STALLED_UP,
                    Torrent.State.QUEUED_UP,
                )
        StateFilter.STOPPED ->
            state in
                setOf(
                    Torrent.State.PAUSED_DL,
                    Torrent.State.STOPPED_DL,
                    Torrent.State.PAUSED_UP,
                    Torrent.State.STOPPED_UP,
                )
        StateFilter.COMPLETED -> amountLeft == 0L
        StateFilter.ERRORED -> state in setOf(Torrent.State.ERROR, Torrent.State.MISSING_FILES)
    }

// The per-dimension drawer filters. Kept here as the single source of truth so the
// ServerViewModel filter pipeline and the drawer's per-filter counts can't drift apart.

/** Matches a category path, treating it as a subtree so a parent counts its children. */
fun Torrent.matchesCategory(category: String?): Boolean =
    category == null || this.category == category || this.category.startsWith("$category/")

/** Matches on the tracker URL's host, which is what the drawer's tracker rows hold. */
fun Torrent.matchesTracker(host: String?): Boolean =
    host == null || Uri.parse(tracker).host.equals(host, ignoreCase = true)

fun Torrent.matchesTags(selectedTags: Set<String>, filterUntagged: Boolean): Boolean =
    when {
        filterUntagged -> tags.isEmpty()
        selectedTags.isEmpty() -> true
        else -> selectedTags.any { tags.contains(it) }
    }

fun Torrent.matchesSearch(query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true)
