package dev.yashgarg.qbit.utils

import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExceptionsTest {
    @Test
    fun `SSLHandshakeException caused by CertPathValidatorException is an untrusted certificate error`() {
        val ex = SSLHandshakeException("handshake failed")
        ex.initCause(CertPathValidatorException("no trust anchor"))
        assertTrue(ex.isUntrustedCertificateError())
    }

    @Test
    fun `SSLHandshakeException with a PKIX path message is an untrusted certificate error`() {
        val ex = SSLHandshakeException("PKIX path building failed")
        assertTrue(ex.isUntrustedCertificateError())
    }

    @Test
    fun `a generic SSLException is not an untrusted certificate error`() {
        assertFalse(SSLException("protocol error").isUntrustedCertificateError())
    }

    @Test
    fun `an SSLHandshakeException unrelated to trust is not an untrusted certificate error`() {
        assertFalse(
            SSLHandshakeException("no cipher suites in common").isUntrustedCertificateError()
        )
    }
}
