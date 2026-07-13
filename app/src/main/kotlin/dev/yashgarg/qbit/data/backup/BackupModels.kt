package dev.yashgarg.qbit.data.backup

import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.ServerPreferences
import kotlinx.serialization.Serializable

/** The decrypted payload of a backup: every saved server plus the app preferences. */
@Serializable
data class ConfigBackup(
    val servers: List<ServerConfig>,
    val preferences: ServerPreferences,
)

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
