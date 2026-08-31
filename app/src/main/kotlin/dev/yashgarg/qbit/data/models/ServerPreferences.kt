package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** Per-server filter + sort selections, so each server restores its own view. */
@Keep
@Serializable
data class ServerViewPrefs(
    val sortOptionName: String = "NAME",
    val sortDirectionAsc: Boolean = true,
    val filterStateName: String = "ALL",
    val filterCategory: String? = null,
    val filterTracker: String? = null,
    val filterTags: Set<String> = emptySet(),
    val filterUntagged: Boolean = false,
)

@Keep
@Serializable
data class ServerPreferences(
    val addTorrentAutoTmm: Boolean = false,
    val addTorrentPaused: Boolean = false,
    val addTorrentCategory: String = "",
    /** Per-server view state (filters + sort), keyed by [ServerConfig.configId]. */
    val serverViewPrefs: Map<Int, ServerViewPrefs> = emptyMap(),
    val dynamicColors: Boolean = false,
    val activeServerId: Int = -1,
    val categoryColors: Map<String, Int> = emptyMap(),
    val statusNotification: Boolean = true,
    val notifyOnComplete: Boolean = false,
    val notifyOnChecked: Boolean = false,
    // Set once the notification-permission rationale dialog has been answered (either way), so it
    // only ever shows once per install rather than nagging on every launch.
    val notificationPermissionAsked: Boolean = false,
    // Same idea for the local-network-permission rationale dialog (Android 17+, needed to reach a
    // self-hosted server on the LAN).
    val localNetworkPermissionAsked: Boolean = false,
    // Newest torrent completion_on (unix seconds) already accounted for by the complete-notifier,
    // keyed by server id. Lets completion alerts survive the worker process restarting: a torrent
    // whose completion_on is newer than this still fires. Written only when it advances.
    val notifCompletionSeen: Map<Int, Long> = emptyMap(),
    // Hashes that were being checked as of the last poll, keyed by server id. The checked-notifier
    // compares each poll against this set and alerts for torrents that have since left it, so
    // recheck alerts survive the worker restarting and don't need a live in-memory baseline.
    val notifCheckingSeen: Map<Int, Set<String>> = emptyMap(),
    // Set true when a completion/checked notification is turned ON, so the worker's next poll
    // adopts
    // the current state as a silent baseline (instead of replaying everything that finished while
    // the toggle was off). Cleared once consumed. Not set on worker restart, so downtime
    // completions still alert.
    val notifCompleteRebaseline: Boolean = false,
    val notifCheckedRebaseline: Boolean = false,
    val notifyOnNewRssArticles: Boolean = false,
    // Article ids already accounted for by the RSS notifier, keyed by "$serverId|$feedPath" (a
    // feed path alone isn't unique across servers). Ids for articles no longer present in a feed's
    // current fetch are dropped on the next poll, so this stays bounded by each feed's own
    // (server-capped) article list instead of growing forever.
    val notifRssArticleSeen: Map<String, Set<String>> = emptyMap(),
    // Set true when the RSS notification is turned ON, so the worker's next poll adopts every
    // feed's current article set as a silent baseline instead of alerting for the whole backlog.
    val notifRssRebaseline: Boolean = false,
    // AppCompatDelegate night-mode constant. Defaults to MODE_NIGHT_YES (2) to preserve the
    // app's original dark-only behaviour for existing installs.
    val themeMode: Int = 2,
    // Highest versionCode whose "What's New" has been shown. 0 = never recorded (fresh install),
    // so the dialog is skipped on first run and only appears after an upgrade.
    val lastSeenVersionCode: Int = 0,
    // How often StatusWorker re-fetches global transfer speeds for the status notification, in
    // milliseconds. Independent of eventPollIntervalMs so completion/recheck alerts can stay
    // prompt even when this is slowed down for battery.
    val statusRefreshIntervalMs: Long = 5_000L,
    // How often StatusWorker polls the torrent list to detect completions/rechecks, in
    // milliseconds.
    val eventPollIntervalMs: Long = 5_000L,
    // How often the live torrent-list sync (while a server screen is open) polls the server, in
    // milliseconds.
    val syncIntervalMs: Long = 5_000L,
)
