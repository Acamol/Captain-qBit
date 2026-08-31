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
}
