package dev.yashgarg.qbit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.*

/**
 * Cancellation means the caller went away, not that the server failed. ErrorTransformer turns
 * pre-request throwables into a synthetic failed response so they surface as QBittorrentException -
 * doing that to a CancellationException misreports it as a sync failure (the app flashed "failed to
 * sync data" on every server switch) and swallows the cancellation the coroutine machinery needs.
 */
class CancellationNotAnErrorTest {

    private fun client(engine: MockEngine) =
        QBittorrentClient(
            baseUrl = "http://localhost",
            httpClient = HttpClient(engine),
            dispatcher = Dispatchers.Default,
        )

    @Test
    fun `a cancelled request is not reported as a server error`() {
        val c = client(MockEngine { throw CancellationException("cancelled mid-request") })
        var thrown: Throwable? = null
        try {
            // Bounded: swallowing the cancellation and re-entering the pipeline leaves the request
            // unable to complete, so without the fix this hangs rather than failing. The timeout
            // turns that into an assertion failure instead of a stuck build.
            runBlocking { withTimeout(5_000) { c.getVersion() } }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            c.close()
        }
        assertFalse(
            "cancellation must not be wrapped as a server error, got $thrown",
            thrown is QBittorrentException,
        )
        assertFalse(
            "the request must not hang when cancelled, got $thrown",
            thrown is TimeoutCancellationException,
        )
        assertTrue("expected a CancellationException, got $thrown", thrown is CancellationException)
    }

    @Test
    fun `a genuine request failure is still reported as a server error`() {
        // Not cancellation: this must keep its existing QBittorrentException treatment so real
        // network problems still reach the user.
        val c = client(MockEngine { throw java.io.IOException("connection reset") })
        var thrown: Throwable? = null
        try {
            runBlocking { c.getVersion() }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            c.close()
        }
        assertTrue("expected a QBittorrentException, got $thrown", thrown is QBittorrentException)
    }

    @Test
    fun `a working request still succeeds`() {
        val c = client(MockEngine { respond("v5.2.3") })
        try {
            assertEquals("v5.2.3", runBlocking { c.getVersion() })
        } finally {
            c.close()
        }
    }
}
