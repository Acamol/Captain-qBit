package dev.yashgarg.qbit.data.manager

import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ClientManagerTlsTest {
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
    fun `a client with no pin fails against a self-signed server`() = runTest {
        server.enqueue(MockResponse(code = 200))
        val client = ClientManager.httpClient()
        try {
            client.get("https://localhost:${server.port}/")
            fail("expected the handshake to fail against an untrusted self-signed certificate")
        } catch (_: Exception) {
            // expected
        } finally {
            client.close()
        }
    }

    @Test
    fun `a client pinned to the server's own certificate succeeds`() = runTest {
        server.enqueue(MockResponse(code = 200))
        val client =
            ClientManager.httpClient(pinnedCertificateDer = heldCertificate.certificate.encoded)
        val response = client.get("https://localhost:${server.port}/")
        assertEquals(200, response.status.value)
        client.close()
    }

    @Test
    fun `a client pinned to a different certificate still fails`() = runTest {
        val otherCert = HeldCertificate.Builder().commonName("other").build()
        server.enqueue(MockResponse(code = 200))
        val client = ClientManager.httpClient(pinnedCertificateDer = otherCert.certificate.encoded)
        try {
            client.get("https://localhost:${server.port}/")
            fail("a pin for a different certificate must not trust this server")
        } catch (_: Exception) {
            // expected
        } finally {
            client.close()
        }
    }

    @Test
    fun `a pinned certificate with no subjectAltName is accepted`() = runTest {
        // What people actually generate for a home server: a Common Name and nothing else.
        // OkHttp's hostname verifier rejects these outright, so pinning has to establish identity
        // from the certificate itself for the feature to be usable at all.
        val cnOnly = HeldCertificate.Builder().commonName("localhost").build()
        val certs = HandshakeCertificates.Builder().heldCertificate(cnOnly).build()
        val cnServer = MockWebServer()
        cnServer.useHttps(certs.sslSocketFactory())
        cnServer.start()
        cnServer.enqueue(MockResponse(code = 200))

        val client = ClientManager.httpClient(pinnedCertificateDer = cnOnly.certificate.encoded)
        try {
            assertEquals(200, client.get("https://localhost:${cnServer.port}/").status.value)
        } finally {
            client.close()
            cnServer.close()
        }
    }

    @Test
    fun `pinning a CA certificate does not trust other certificates it signed`() = runTest {
        // openssl's `req -x509` default sets CA:TRUE, so a pinned server certificate can happen to
        // be a usable issuer. Approving one certificate must not silently vouch for another.
        val ca = HeldCertificate.Builder().commonName("home-ca").certificateAuthority(0).build()
        // A subjectAltName so this leaf would otherwise satisfy hostname verification - without it
        // the connection would fail for that unrelated reason and the test would prove nothing.
        val signed =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .signedBy(ca)
                .build()
        val certs = HandshakeCertificates.Builder().heldCertificate(signed, ca.certificate).build()
        val caServer = MockWebServer()
        caServer.useHttps(certs.sslSocketFactory())
        caServer.start()
        caServer.enqueue(MockResponse(code = 200))

        val client = ClientManager.httpClient(pinnedCertificateDer = ca.certificate.encoded)
        try {
            client.get("https://localhost:${caServer.port}/")
            fail("a pin must only match the approved certificate, not others it signed")
        } catch (_: Exception) {
            // expected
        } finally {
            client.close()
            caServer.close()
        }
    }
}
