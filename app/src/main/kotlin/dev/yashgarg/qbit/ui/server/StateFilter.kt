package dev.yashgarg.qbit.ui.server

import android.net.Uri
import androidx.annotation.StringRes
import dev.yashgarg.qbit.common.R
import qbittorrent.models.Torrent

// The Status filters, matching qBittorrent's own sidebar set and order, plus a "Queued" filter for
// the torrents that currently hold a queue position.
enum class StateFilter(@StringRes val labelRes: Int) {
    ALL(R.string.all_label),
    DOWNLOADING(R.string.filter_downloading),
    QUEUED(R.string.filter_queued),
    SEEDING(R.string.seeding),
    COMPLETED(R.string.completed),
    RUNNING(R.string.filter_running),
    STOPPED(R.string.stopped),
    ACTIVE(R.string.filter_active),
    INACTIVE(R.string.filter_inactive),
    STALLED(R.string.stalled),
    STALLED_UPLOADING(R.string.filter_stalled_uploading),
    STALLED_DOWNLOADING(R.string.filter_stalled_downloading),
    CHECKING(R.string.state_checking),
    MOVING(R.string.state_moving),
    ERRORED(R.string.state_errored),
}

private val downloadingStates =
    setOf(
        Torrent.State.DOWNLOADING,
        Torrent.State.FORCED_DL,
        Torrent.State.META_DL,
        Torrent.State.FORCED_META_DL,
        Torrent.State.ALLOCATING,
        Torrent.State.QUEUED_DL,
        Torrent.State.STALLED_DL,
    )

private val seedingStates =
    setOf(
        Torrent.State.UPLOADING,
        Torrent.State.FORCED_UP,
        Torrent.State.STALLED_UP,
        Torrent.State.QUEUED_UP,
    )

private val stoppedStates =
    setOf(
        Torrent.State.PAUSED_DL,
        Torrent.State.STOPPED_DL,
        Torrent.State.PAUSED_UP,
        Torrent.State.STOPPED_UP,
    )

private val checkingStates =
    setOf(
        Torrent.State.CHECKING_DL,
        Torrent.State.CHECKING_UP,
        Torrent.State.CHECKING_RESUME_DATA,
    )

fun Torrent.matchesFilter(filter: StateFilter): Boolean =
    when (filter) {
        StateFilter.ALL -> true
        StateFilter.DOWNLOADING -> state in downloadingStates
        StateFilter.QUEUED -> priority > 0
        StateFilter.SEEDING -> state in seedingStates
        StateFilter.COMPLETED -> amountLeft == 0L
        StateFilter.RUNNING -> state !in stoppedStates
        StateFilter.STOPPED -> state in stoppedStates
        StateFilter.ACTIVE -> dlspeed > 0 || uploadSpeed > 0
        StateFilter.INACTIVE -> dlspeed == 0L && uploadSpeed == 0L
        StateFilter.STALLED ->
            state == Torrent.State.STALLED_DL || state == Torrent.State.STALLED_UP
        StateFilter.STALLED_UPLOADING -> state == Torrent.State.STALLED_UP
        StateFilter.STALLED_DOWNLOADING -> state == Torrent.State.STALLED_DL
        StateFilter.CHECKING -> state in checkingStates
        StateFilter.MOVING -> state == Torrent.State.MOVING
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

/**
 * AND-of-words, order-independent, case-insensitive - same semantics as the RSS article search and
 * a single line of an RSS rule's "Must contain" field.
 */
fun Torrent.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.all { name.contains(it, ignoreCase = true) }
}
