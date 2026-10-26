package dev.yashgarg.qbit.data.backup

import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.serialization.Serializable

/** Independently selectable groups of app preferences for export/import. */
@Serializable
enum class PrefGroup {
    APPEARANCE,
    NOTIFICATIONS,
    FILTERS,
    PRIVACY,
}

/**
 * The decrypted payload of a backup: the chosen servers plus, optionally, app-preference groups and
 * category colors. [preferences] is a snapshot holding only the groups listed in [preferenceGroups]
 * (other fields are defaults). Category colors are stored separately so they can be exported/
 * imported on their own. Any field is null/empty when the user chose not to export it.
 */
@Serializable
data class ConfigBackup(
    val servers: List<ServerConfig>,
    val preferences: AppPreferences? = null,
    val preferenceGroups: Set<PrefGroup> = emptySet(),
    val categoryColors: Map<String, Int>? = null,
    val privacyPreferences: AppPreferences? = null,
)

/**
 * Builds the payload for [groups] of [prefs]. The privacy group gets a field of its own instead of
 * going into the shared preferences and group list: older app versions fail on a group name they
 * don't know, and would reset every other group to its defaults if a backup carried only this one.
 */
fun configBackupOf(
    servers: List<ServerConfig>,
    prefs: AppPreferences?,
    groups: Set<PrefGroup>,
    categoryColors: Map<String, Int>?,
): ConfigBackup {
    val legacyGroups = groups - PrefGroup.PRIVACY
    return ConfigBackup(
        servers = servers,
        preferences =
            if (prefs != null && legacyGroups.isNotEmpty()) prefs.extractGroups(legacyGroups)
            else null,
        preferenceGroups = legacyGroups,
        categoryColors = categoryColors,
        privacyPreferences =
            if (prefs != null && PrefGroup.PRIVACY in groups) {
                prefs.extractGroups(setOf(PrefGroup.PRIVACY))
            } else null,
    )
}

/**
 * Which preference groups this backup actually carries. Backups written before groups existed only
 * set [ConfigBackup.preferences]; treat those as carrying every group that existed then. Privacy
 * came later, so such a backup has none and must not overwrite the current settings.
 */
fun ConfigBackup.availablePrefGroups(): Set<PrefGroup> {
    val groups =
        when {
            preferenceGroups.isNotEmpty() -> preferenceGroups
            preferences != null -> PrefGroup.entries.toSet() - PrefGroup.PRIVACY
            else -> emptySet()
        }
    return if (privacyPreferences != null) groups + PrefGroup.PRIVACY else groups
}

/** Copies the fields belonging to [group] from [src] onto this preferences object. */
fun AppPreferences.overlayGroup(group: PrefGroup, src: AppPreferences): AppPreferences =
    when (group) {
        PrefGroup.APPEARANCE ->
            copy(
                themeMode = src.themeMode,
                dynamicColors = src.dynamicColors,
                languageTag = src.languageTag,
            )
        PrefGroup.NOTIFICATIONS ->
            copy(
                statusNotification = src.statusNotification,
                notifyOnComplete = src.notifyOnComplete,
                notifyOnChecked = src.notifyOnChecked,
                notifyOnNewRssArticles = src.notifyOnNewRssArticles,
                eventAlertMode = src.eventAlertMode,
                quietHoursStartMinutes = src.quietHoursStartMinutes,
                quietHoursEndMinutes = src.quietHoursEndMinutes,
                statusRefreshIntervalMs = src.statusRefreshIntervalMs,
                eventPollIntervalMs = src.eventPollIntervalMs,
                syncIntervalMs = src.syncIntervalMs,
            )
        // Filters also covers the per-server sort/filter view state and the add-torrent defaults —
        // the rest of the torrent-list state.
        PrefGroup.FILTERS ->
            copy(
                serverViewPrefs = src.serverViewPrefs,
                addTorrentAutoTmm = src.addTorrentAutoTmm,
                addTorrentPaused = src.addTorrentPaused,
                addTorrentCategory = src.addTorrentCategory,
            )
        // A restored lock on a device without a screen lock is turned off again by AppLockManager.
        PrefGroup.PRIVACY ->
            copy(
                appLockEnabled = src.appLockEnabled,
                appLockTimeoutMs = src.appLockTimeoutMs,
                appLockOnScreenOff = src.appLockOnScreenOff,
                hideInRecents = src.hideInRecents,
                blockScreenshots = src.blockScreenshots,
            )
    }

/**
 * Builds a snapshot containing only [groups]' fields from this preferences object (rest default).
 */
fun AppPreferences.extractGroups(groups: Set<PrefGroup>): AppPreferences =
    groups.fold(AppPreferences()) { acc, group -> acc.overlayGroup(group, this) }

/** How an import reconciles the backup's servers with the ones already saved. */
enum class ImportMode {
    /** Add the backup's servers to the current set, skipping ones that already exist. */
    MERGE,
    /** Replace every current server (and, if selected, the preferences) with the backup's. */
    REPLACE,
}

/**
 * Outcome of an import: how many servers were added, how many were skipped as duplicates (merge
 * only), and whether the import replaced the existing configuration wholesale.
 */
data class ImportResult(val imported: Int, val skipped: Int, val replaced: Boolean)

/**
 * The on-disk file format. The sensitive [ConfigBackup] is encrypted with AES-256-GCM using a key
 * derived from the user's passphrase (PBKDF2), so credentials never touch the file in cleartext.
 * [salt], [iv], and [ciphertext] are Base64-encoded.
 */
@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
) {
    companion object {
        const val FORMAT = "captain-qbit-backup"
        const val VERSION = 1
    }
}
