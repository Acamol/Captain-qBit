package dev.yashgarg.qbit.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Raw output of [BackupCrypto.encrypt]; all fields are needed to decrypt again. */
data class EncryptedPayload(
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val iterations: Int = BackupCrypto.KDF_ITERATIONS,
)

/**
 * Passphrase-based authenticated encryption for config backups.
 *
 * A 256-bit AES key is derived from the passphrase with PBKDF2-HMAC-SHA256 over a random salt, then
 * used with AES/GCM/NoPadding (a random IV per encryption, 128-bit auth tag). GCM authenticates the
 * ciphertext, so a wrong passphrase or a tampered file fails decryption with an exception rather
 * than yielding garbage.
 */
object BackupCrypto {
    const val KDF_ITERATIONS = 75_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    // [payload]'s iterations count comes straight from the backup file's own (unauthenticated)
    // envelope, so it must be bounds-checked before it reaches PBKDF2 — an unbounded value from a
    // corrupted or crafted file would otherwise make key derivation hang for minutes to hours.
    private const val MIN_ITERATIONS = 1
    private const val MAX_ITERATIONS = 1_000_000

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): EncryptedPayload =
        encrypt(plaintext, passphrase, KDF_ITERATIONS)

    /** Exposed at a specific iteration count only so tests can simulate an old backup. */
    internal fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int,
    ): EncryptedPayload {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    deriveKey(passphrase, salt, iterations),
                    GCMParameterSpec(GCM_TAG_BITS, iv),
                )
            }
        return EncryptedPayload(salt, iv, cipher.doFinal(plaintext), iterations)
    }

    /**
     * Throws if the passphrase is wrong or the ciphertext was altered (GCM tag mismatch), or if
     * [payload]'s claimed iteration count is outside [MIN_ITERATIONS]..[MAX_ITERATIONS] (a
     * corrupted or crafted file). Otherwise uses [payload]'s own iteration count (not the current
     * [KDF_ITERATIONS]) so a backup written before that constant was last raised still decrypts
     * correctly.
     */
    fun decrypt(payload: EncryptedPayload, passphrase: CharArray): ByteArray {
        if (payload.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw InvalidBackupException("This file isn't a Captain qBit backup")
        }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(passphrase, payload.salt, payload.iterations),
                    GCMParameterSpec(GCM_TAG_BITS, payload.iv),
                )
            }
        return cipher.doFinal(payload.ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val keyBytes =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
