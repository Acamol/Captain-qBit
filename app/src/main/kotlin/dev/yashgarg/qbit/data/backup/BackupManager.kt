package dev.yashgarg.qbit.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.CryptoManager
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.utils.LocalizedContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Thrown when an imported file isn't a Captain qBit backup or is empty. */
class InvalidBackupException(message: String) : Exception(message)

/**
 * Exports and restores app configuration (chosen servers + optional preferences) to/from an
 * encrypted file the user picks via the Storage Access Framework. Import is a two-step flow:
 * [readBackup] decrypts the file so the caller can present a selection, then [applyImport] either
 * merges the chosen servers into the current set or replaces everything with them.
 */
@Singleton
class BackupManager
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val configDao: ConfigDao,
    private val prefsStore: DataStore<AppPreferences>,
    private val clientManager: ClientManager,
) {

    // These strings become exception messages that BackupViewModel re-surfaces verbatim as toasts,
    // so they have to follow the language chosen in the app - which the injected application
    // context does not below API 33.
    private val localized: Context
        get() = LocalizedContext.of(context)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Encrypts [servers] (and [preferences], when non-null) to the file at [uri]. The caller is
     * responsible for having filtered these down to what the user chose to export.
     */
    suspend fun export(
        uri: Uri,
        passphrase: String,
        servers: List<ServerConfig>,
        preferences: AppPreferences?,
        preferenceGroups: Set<PrefGroup>,
        categoryColors: Map<String, Int>?,
    ) =
        withContext(Dispatchers.IO) {
            // Store credentials as plaintext inside the (passphrase-encrypted) payload. The DB
            // keeps
            // them encrypted with a device-bound Keystore key that can't be restored elsewhere, so
            // exporting that ciphertext would be undecryptable on another device or a fresh
            // install.
            val backup =
                ConfigBackup(
                    servers.map { it.withDecryptedSecrets() },
                    preferences,
                    preferenceGroups,
                    categoryColors,
                )
            val plaintext =
                json.encodeToString(ConfigBackup.serializer(), backup).encodeToByteArray()

            val chars = passphrase.toCharArray()
            val encrypted =
                try {
                    BackupCrypto.encrypt(plaintext, chars)
                } finally {
                    chars.fill('\u0000')
                }

            val envelope =
                BackupEnvelope(
                    iterations = encrypted.iterations,
                    salt = encrypted.salt.toBase64(),
                    iv = encrypted.iv.toBase64(),
                    ciphertext = encrypted.ciphertext.toBase64(),
                )
            val bytes =
                json.encodeToString(BackupEnvelope.serializer(), envelope).encodeToByteArray()

            (context.contentResolver.openOutputStream(uri)
                    ?: throw InvalidBackupException(
                        localized.getString(CommonR.string.error_could_not_open_file)
                    ))
                .use { it.write(bytes) }
        }

    /**
     * Decrypts and validates the backup at [uri] without touching the database, so the caller can
     * show the user what's inside before choosing what to import. Throws [InvalidBackupException]
     * for a non-backup/empty file and [javax.crypto.AEADBadTagException] for a wrong passphrase.
     */
    suspend fun readBackup(uri: Uri, passphrase: String): ConfigBackup =
        withContext(Dispatchers.IO) {
            val raw =
                (context.contentResolver.openInputStream(uri)
                        ?: throw InvalidBackupException(
                            localized.getString(CommonR.string.error_could_not_open_file)
                        ))
                    .use { it.readBytes() }

            val envelope =
                try {
                    json.decodeFromString(BackupEnvelope.serializer(), raw.decodeToString())
                } catch (e: Exception) {
                    throw InvalidBackupException(
                        localized.getString(CommonR.string.error_not_a_backup_file)
                    )
                }
            if (envelope.format != BackupEnvelope.FORMAT) {
                throw InvalidBackupException(
                    localized.getString(CommonR.string.error_not_a_backup_file)
                )
            }

            val chars = passphrase.toCharArray()
            val plaintext =
                try {
                    BackupCrypto.decrypt(
                        EncryptedPayload(
                            envelope.salt.fromBase64(),
                            envelope.iv.fromBase64(),
                            envelope.ciphertext.fromBase64(),
                            envelope.iterations,
                        ),
                        chars,
                    )
                } finally {
                    chars.fill('\u0000')
                }

            val backup =
                json.decodeFromString(ConfigBackup.serializer(), plaintext.decodeToString())
            if (backup.servers.isEmpty()) {
                throw InvalidBackupException(
                    localized.getString(CommonR.string.error_backup_no_servers)
                )
            }
            backup
        }

    /**
     * Applies a previously [readBackup]-ed configuration. [selectedServers] must be a subset of
     * [backup]'s servers; [selectedPrefGroups] chooses which app-preference groups to apply and
     * [includeCategoryColors] its category colors, each independently and ignored when the backup
     * lacks them. See [ImportMode] for merge vs. replace semantics.
     */
    suspend fun applyImport(
        backup: ConfigBackup,
        selectedServers: List<ServerConfig>,
        selectedPrefGroups: Set<PrefGroup>,
        includeCategoryColors: Boolean,
        mode: ImportMode,
    ): ImportResult =
        withContext(Dispatchers.IO) {
            when (mode) {
                ImportMode.REPLACE -> {
                    configDao.clearConfigs()
                    // Re-encrypt the plaintext backup credentials with this device's Keystore key.
                    // Position is reassigned from the backup's own server order rather than trusted
                    // as-is, since it may be stale or absent (backups written before this field
                    // existed default it to 0 for every server).
                    selectedServers.forEachIndexed { index, server ->
                        configDao.addConfig(server.copy(position = index).withEncryptedSecrets())
                    }
                    applyPreferences(backup, selectedPrefGroups, includeCategoryColors)

                    // Point the client at a valid server: the backup's active one if it's among the
                    // restored servers, otherwise the first restored server.
                    val ids = selectedServers.map { it.configId }
                    val active =
                        backup.preferences?.activeServerId?.takeIf { it in ids } ?: ids.first()
                    clientManager.setActiveServer(active)
                    ImportResult(selectedServers.size, skipped = 0, replaced = true)
                }
                ImportMode.MERGE -> {
                    val existing = configDao.getConfigs().first()
                    val existingKeys = existing.map(::identityKey).toHashSet()
                    val hadNoServers = existing.isEmpty()

                    var nextId = configDao.maxConfigId() + 1
                    // Merged-in servers land after the ones already saved, in selection order.
                    var nextPosition = configDao.maxPosition() + 1
                    var imported = 0
                    var skipped = 0
                    var firstNewId: Int? = null
                    // Backup-side configId -> newly assigned id, so the FILTERS group's
                    // per-server serverViewPrefs (keyed by the backup's original ids) can be
                    // remapped onto the ids the merged servers actually get below.
                    val configIdRemap = mutableMapOf<Int, Int>()
                    for (server in selectedServers) {
                        if (!existingKeys.add(identityKey(server))) {
                            skipped++
                            continue
                        }
                        val id = nextId++
                        configIdRemap[server.configId] = id
                        // Re-encrypt the plaintext backup credentials with this device's key.
                        configDao.addConfig(
                            server
                                .copy(configId = id, position = nextPosition++)
                                .withEncryptedSecrets()
                        )
                        if (firstNewId == null) firstNewId = id
                        imported++
                    }

                    applyPreferences(
                        backup,
                        selectedPrefGroups,
                        includeCategoryColors,
                        configIdRemap,
                    )
                    // First-run restore into an empty app: land on a live server.
                    if (hadNoServers && firstNewId != null) {
                        clientManager.setActiveServer(firstNewId)
                    }
                    ImportResult(imported, skipped, replaced = false)
                }
            }
        }

    /**
     * Overlays the selected preference groups and, independently, category colors onto the current
     * preferences in one update. Groups (and colors) the user didn't select are left untouched, as
     * are activeServerId and — unless the colors toggle is on — the current category colors. Falls
     * back to colors embedded in [ConfigBackup.preferences] for backups written before colors
     * split.
     *
     * [configIdRemap] (backup-side configId -> newly assigned id) is non-empty only for a MERGE
     * import: since merging reassigns each imported server a fresh id, the FILTERS group's
     * per-server `serverViewPrefs` (keyed by the backup's original ids) can't be applied verbatim
     * like the other groups' plain fields - it's remapped and merged into the existing map instead
     * of overwriting it, so pre-existing servers keep their own saved views.
     */
    private suspend fun applyPreferences(
        backup: ConfigBackup,
        selectedPrefGroups: Set<PrefGroup>,
        includeCategoryColors: Boolean,
        configIdRemap: Map<Int, Int> = emptyMap(),
    ) {
        val groups = backup.availablePrefGroups() intersect selectedPrefGroups
        val src = backup.preferences
        val backupColors = backup.categoryColors ?: backup.preferences?.categoryColors
        val applyColors = includeCategoryColors && backupColors != null
        if ((groups.isEmpty() || src == null) && !applyColors) return

        prefsStore.updateData { current ->
            var result = current
            if (src != null) {
                groups.forEach { group ->
                    result =
                        if (group == PrefGroup.FILTERS && configIdRemap.isNotEmpty()) {
                            val remapped =
                                src.serverViewPrefs
                                    .mapNotNull { (oldId, viewPrefs) ->
                                        configIdRemap[oldId]?.let { newId -> newId to viewPrefs }
                                    }
                                    .toMap()
                            result
                                .overlayGroup(group, src)
                                .copy(serverViewPrefs = result.serverViewPrefs + remapped)
                        } else {
                            result.overlayGroup(group, src)
                        }
                }
            }
            if (applyColors) result = result.copy(categoryColors = backupColors)
            result
        }
    }

    /** Decrypts the at-rest credential fields so the backup payload holds plaintext. */
    private fun ServerConfig.withDecryptedSecrets(): ServerConfig =
        copy(
            password = CryptoManager.decrypt(password) ?: password,
            basicAuthPassword = CryptoManager.decrypt(basicAuthPassword),
        )

    /**
     * Re-encrypts the backup credentials with this device's Keystore key for storage. Decrypts
     * first so this is correct for new plaintext backups (a no-op decrypt) and also normalizes an
     * older backup that still holds ciphertext when it's re-imported on the same device.
     */
    private fun ServerConfig.withEncryptedSecrets(): ServerConfig =
        copy(
            password = CryptoManager.encrypt(CryptoManager.decrypt(password)) ?: password,
            basicAuthPassword = CryptoManager.encrypt(CryptoManager.decrypt(basicAuthPassword)),
        )

    /** Identity keys ([identityKey]) of the servers currently saved, for duplicate detection. */
    suspend fun currentServerKeys(): Set<String> =
        withContext(Dispatchers.IO) {
            configDao.getConfigs().first().map(::identityKey).toHashSet()
        }

    /** Normalized identity used to detect a server already present during a merge. */
    fun identityKey(config: ServerConfig): String =
        listOf(
                config.serverName.trim(),
                config.baseUrl.trim().trimEnd('/'),
                config.port?.toString().orEmpty(),
                config.path?.trim().orEmpty(),
            )
            .joinToString(" ")
            .lowercase()

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
