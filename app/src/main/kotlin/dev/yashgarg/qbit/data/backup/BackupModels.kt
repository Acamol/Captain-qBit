package dev.yashgarg.qbit.data.backup

import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import kotlinx.serialization.Serializable

/** Independently selectable groups of app preferences for export/import. */
@Serializable
enum class PrefGroup {
    APPEARANCE,
    NOTIFICATIONS,
    FILTERS,
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
    val preferences: ServerPreferences? = null,
    val preferenceGroups: Set<PrefGroup> = emptySet(),
    val categoryColors: Map<String, Int>? = null,
)

/**
 * Which preference groups this backup actually carries. Backups written before groups existed only
 * set [ConfigBackup.preferences]; treat those as carrying every group.
 */
fun ConfigBackup.availablePrefGroups(): Set<PrefGroup> =
    when {
        preferenceGroups.isNotEmpty() -> preferenceGroups
        preferences != null -> PrefGroup.entries.toSet()
        else -> emptySet()
    }

/** Copies the fields belonging to [group] from [src] onto this preferences object. */
fun ServerPreferences.overlayGroup(group: PrefGroup, src: ServerPreferences): ServerPreferences =
    when (group) {
        PrefGroup.APPEARANCE -> copy(themeMode = src.themeMode, dynamicColors = src.dynamicColors)
        PrefGroup.NOTIFICATIONS ->
            copy(
                statusNotification = src.statusNotification,
                notifyOnComplete = src.notifyOnComplete,
                notifyOnChecked = src.notifyOnChecked,
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
    }

/**
 * Builds a snapshot containing only [groups]' fields from this preferences object (rest default).
 */
fun ServerPreferences.extractGroups(groups: Set<PrefGroup>): ServerPreferences =
    groups.fold(ServerPreferences()) { acc, group -> acc.overlayGroup(group, this) }

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
