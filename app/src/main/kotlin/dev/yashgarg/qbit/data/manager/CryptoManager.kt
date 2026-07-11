package dev.yashgarg.qbit.data.manager

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive config fields (server passwords) at rest using an AES-256/GCM key held in the
 * Android Keystore. Stored values are prefixed so plaintext written by older versions is still
 * readable and gets re-encrypted on the next save. All operations fail soft — if the Keystore is
 * unavailable, the original value is returned rather than losing the credential.
 */
object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "qbit_cred_key"
    private const val PREFIX = "enc1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed, storing value unencrypted", e)
            value
        }
    }

    fun decrypt(value: String?): String? {
        if (value == null || !value.startsWith(PREFIX)) return value
        return try {
            val bytes = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_LEN)
            val body = bytes.copyOfRange(IV_LEN, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed, returning stored value as-is", e)
            value
        }
    }
}
