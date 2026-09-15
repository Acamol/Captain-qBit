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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.*

/**
 * qBittorrent >= 5.2 authenticates an API key per request via a Bearer header, and rejects keys at
 * the auth endpoints outright - so a key must be sent on every call and must never trigger a login.
 */
class ApiKeyAuthTest {
    private val requestedPaths = mutableListOf<String>()
    private val authHeaders = mutableListOf<String?>()

    // Fixture values only. The MockEngine accepts anything, so there is nothing real here: the key
    // just has to look like a key for the header assertion, and the login fixtures only have to be
    // non-empty to select the session-auth path.
    private val fixtureKey = "qbt_placeholderplaceholderxx"
    private val fixtureUser = "placeholder-user"
    private val fixtureSecret = "placeholder-secret"

    private fun client(
        user: String = "",
        secret: String = "",
        apiKey: String? = null,
    ): QBittorrentClient {
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            authHeaders += request.headers[HttpHeaders.Authorization]
            if (request.url.encodedPath.endsWith("/auth/login")) {
                respond("Ok.", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "SID=mock"))
            } else {
                respond("v5.2.0")
            }
        }
        return QBittorrentClient(
            baseUrl = "http://localhost",
            username = user,
            password = secret,
            apiKey = apiKey,
            httpClient = HttpClient(engine),
            dispatcher = Dispatchers.Default,
        )
    }

    private suspend fun fetchVersion(client: QBittorrentClient) {
        try {
            assertEquals("v5.2.0", client.getVersion())
        } finally {
            client.close()
        }
    }

    private fun loggedIn() = requestedPaths.any { it.endsWith("/auth/login") }

    @Test
    fun `an api key is sent as a bearer header`() = runTest {
        fetchVersion(client(apiKey = fixtureKey))
        assertEquals(listOf("Bearer $fixtureKey"), authHeaders)
    }

    @Test
    fun `an api key never triggers a login`() = runTest {
        fetchVersion(client(apiKey = fixtureKey))
        assertFalse(loggedIn())
    }

    @Test
    fun `an api key takes precedence over credentials`() = runTest {
        fetchVersion(client(user = fixtureUser, secret = fixtureSecret, apiKey = fixtureKey))
        assertFalse(loggedIn())
        assertEquals(listOf("Bearer $fixtureKey"), authHeaders)
    }

    @Test
    fun `a blank api key is treated as absent`() = runTest {
        fetchVersion(client(user = fixtureUser, secret = fixtureSecret, apiKey = "   "))
        assertTrue(loggedIn())
        assertTrue(authHeaders.all { it == null })
    }

    @Test
    fun `credentials alone send no authorization header`() = runTest {
        fetchVersion(client(user = fixtureUser, secret = fixtureSecret))
        assertNull(authHeaders.firstOrNull { it != null })
    }
}
