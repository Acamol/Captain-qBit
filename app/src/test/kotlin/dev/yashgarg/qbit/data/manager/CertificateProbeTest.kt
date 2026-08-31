package dev.yashgarg.qbit.data.manager

import com.github.michaelbull.result.get
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class CertificateProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var heldCertificate: HeldCertificate

    @Before
    fun setUp() {
        heldCertificate =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val serverCertificates =
            HandshakeCertificates.Builder().heldCertificate(heldCertificate).build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetches the presented certificate without sending any request`() = runTest {
        val cert = CertificateProbe.fetchPresentedCertificate("localhost", server.port).get()

        assertNotNull(cert)
        assertEquals(heldCertificate.certificate, cert)
        // Proves the probe never talks to the server for real - only the TLS handshake happens.
        assertEquals(0, server.requestCount)
    }
}
