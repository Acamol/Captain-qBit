package dev.yashgarg.qbit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.*

/**
 * A server behind a reverse proxy that authenticates on qBittorrent's behalf is configured with
 * blank credentials; there is no session to establish, so the client should not spend a login
 * round-trip on every request.
 */
class BlankCredentialsAuthTest {
    private val requestedPaths = mutableListOf<String>()

    // Placeholder values, not real credentials - the MockEngine accepts anything.
    private fun client(username: String, password: String): QBittorrentClient {
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("/auth/login")) {
                respond("Ok.", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "SID=mock"))
            } else {
                respond("v4.6.0")
            }
        }
        return QBittorrentClient(
            baseUrl = "http://localhost",
            username = username,
            password = password,
            httpClient = HttpClient(engine),
            dispatcher = Dispatchers.Default,
        )
    }

    private suspend fun fetchVersionWith(username: String, password: String) {
        val client = client(username, password)
        try {
            assertEquals("v4.6.0", client.getVersion())
        } finally {
            client.close()
        }
    }

    @Test
    fun `blank credentials skip the login request`() = runTest {
        fetchVersionWith("", "")
        assertFalse(requestedPaths.any { it.endsWith("/auth/login") })
    }

    @Test
    fun `credentials still establish a session`() = runTest {
        fetchVersionWith("placeholder-user", "placeholder-pass")
        assertTrue(requestedPaths.any { it.endsWith("/auth/login") })
    }
}
