package dev.yashgarg.qbit.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Raw output of [BackupCrypto.encrypt]; all fields are needed to decrypt again. */
data class EncryptedPayload(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

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

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): EncryptedPayload {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    deriveKey(passphrase, salt),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
            }
        return EncryptedPayload(salt, iv, cipher.doFinal(plaintext))
    }

    /** Throws if the passphrase is wrong or the ciphertext was altered (GCM tag mismatch). */
    fun decrypt(payload: EncryptedPayload, passphrase: CharArray): ByteArray {
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    deriveKey(passphrase, payload.salt),
                    GCMParameterSpec(GCM_TAG_BITS, payload.iv),
                )
            }
        return cipher.doFinal(payload.ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, KDF_ITERATIONS, KEY_BITS)
        try {
            val keyBytes =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
