package dev.yashgarg.qbit.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun `round-trips a payload with the current iteration count`() {
        val plaintext = "hello backup".encodeToByteArray()
        val payload = BackupCrypto.encrypt(plaintext, "passphrase".toCharArray())

        assertArrayEquals(plaintext, BackupCrypto.decrypt(payload, "passphrase".toCharArray()))
    }

    @Test
    fun `decrypts using the payload's own iteration count, not the current constant`() {
        // Simulates a backup written before KDF_ITERATIONS was last raised: encrypt at a
        // different count, to prove decrypt() derives its key from the payload's stored count
        // rather than the live constant.
        val plaintext = "hello backup".encodeToByteArray()
        val payload =
            BackupCrypto.encrypt(plaintext, "passphrase".toCharArray(), iterations = 1_000)

        assertArrayEquals(plaintext, BackupCrypto.decrypt(payload, "passphrase".toCharArray()))
    }

    @Test
    fun `wrong passphrase fails with an exception rather than returning garbage`() {
        val plaintext = "hello backup".encodeToByteArray()
        val payload = BackupCrypto.encrypt(plaintext, "correct".toCharArray())

        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(payload, "wrong".toCharArray())
        }
    }

    @Test
    fun `decrypt rejects an implausible iteration count instead of hanging`() {
        val plaintext = "hello backup".encodeToByteArray()
        val payload = BackupCrypto.encrypt(plaintext, "passphrase".toCharArray())
        val tampered = payload.copy(iterations = 2_000_000_000)

        assertThrows(InvalidBackupException::class.java) {
            BackupCrypto.decrypt(tampered, "passphrase".toCharArray())
        }
    }

    @Test
    fun `decrypt rejects a non-positive iteration count`() {
        val plaintext = "hello backup".encodeToByteArray()
        val payload = BackupCrypto.encrypt(plaintext, "passphrase".toCharArray())
        val tampered = payload.copy(iterations = 0)

        assertThrows(InvalidBackupException::class.java) {
            BackupCrypto.decrypt(tampered, "passphrase".toCharArray())
        }
    }
}
