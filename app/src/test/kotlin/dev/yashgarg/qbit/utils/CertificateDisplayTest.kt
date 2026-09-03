package dev.yashgarg.qbit.utils

import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateDisplayTest {
    private val day = 24 * 60 * 60 * 1000L

    private fun certValid(fromOffsetMs: Long, untilOffsetMs: Long) =
        HeldCertificate.Builder()
            .commonName("localhost")
            .validityInterval(
                System.currentTimeMillis() + fromOffsetMs,
                System.currentTimeMillis() + untilOffsetMs,
            )
            .build()
            .certificate

    @Test
    fun `a certificate inside its validity window is neither expired nor pending`() {
        val cert = certValid(-day, day)
        assertFalse(cert.hasExpired())
        assertFalse(cert.isNotYetValid())
    }

    @Test
    fun `a certificate past notAfter has expired`() {
        val cert = certValid(-2 * day, -day)
        assertTrue(cert.hasExpired())
        assertFalse(cert.isNotYetValid())
    }

    @Test
    fun `the formatted date is isolated so RTL sentences don't reorder it`() {
        // Both warnings interpolate this mid-sentence in a translated template, so the neutral
        // characters around it (a full stop, the "until" preposition) must not migrate across it.
        val cert = certValid(-day, day)
        val text = cert.validUntilText()
        assertTrue(text.startsWith("\u2066"))
        assertTrue(text.endsWith("\u2069"))
        assertTrue(text.trim('\u2066', '\u2069').matches(Regex("""\d{2}/\d{2}/\d{4}""")))
    }

    @Test
    fun `a certificate whose notBefore is in the future is not yet valid`() {
        // What a server with a wrong clock issues. Chain validation rejects it just as firmly as an
        // expired one, so approving it can't help and the user needs to be told which case it is.
        val cert = certValid(day, 2 * day)
        assertTrue(cert.isNotYetValid())
        assertFalse(cert.hasExpired())
    }
}
