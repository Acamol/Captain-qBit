package dev.yashgarg.qbit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // JUnit's own timeout, not withTimeout: swallowing the cancellation leaves the request wedged
    // in a state that never resumes, and coroutine-level timeouts need cooperation the wedged
    // coroutine can't give. A watchdog thread fails the test regardless of why it is stuck.
    @Test(timeout = 5_000)
    fun `a cancelled request is not reported as a server error`() {
        val c = client(MockEngine { throw CancellationException("cancelled mid-request") })
        var thrown: Throwable? = null
        try {
            runBlocking { c.getVersion() }
        } catch (t: Throwable) {
            thrown = t
        } finally {
            c.close()
        }
        assertFalse(
            "cancellation must not be wrapped as a server error, got $thrown",
            thrown is QBittorrentException,
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

    private fun maindata() =
        """
        {"rid":1,"full_update":true,"torrents":{},"server_state":{"connection_status":"connected",
                   "dht_nodes":0,"dl_info_data":0,"dl_info_speed":0,"dl_rate_limit":0,"up_info_data":0,
                   "up_info_speed":0,"up_rate_limit":0,"alltime_dl":0,"alltime_ul":0,"average_time_queue":0,
                   "free_space_on_disk":0,"global_ratio":"0","queued_io_jobs":0,"queueing":false,
                   "read_cache_hits":"0","read_cache_overload":"0","refresh_interval":1500,
                   "total_buffers_size":0,"total_peer_connections":0,"total_queued_size":0,
                   "total_wasted_session":0,"write_cache_overload":"0","use_alt_speed_limits":false}}
        """
            .trimIndent()
            .replace("\n", "")

    /**
     * The sync loop itself: closing the client cancels it mid-poll, and that must not leave an
     * error behind for the UI to show. This is the path behind the "failed to sync data" toast on
     * every server switch.
     */
    @Test(timeout = 20_000)
    fun `closing the client mid-sync records no error`() {
        val body = maindata()
        val c =
            QBittorrentClient(
                baseUrl = "http://localhost",
                httpClient =
                    HttpClient(
                        MockEngine {
                            respond(
                                body,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    ),
                syncInterval = 50.milliseconds,
                dispatcher = Dispatchers.Default,
            )
        runBlocking {
            val collector = launch(Dispatchers.Default) { c.observeMainData().collect {} }
            // Let the loop actually start polling before cancelling it.
            withTimeoutOrNull(5_000) { c.observeMainData().first() }
            delay(200)
            c.close()
            delay(500)
            assertNull(
                "cancelling the sync scope must not be recorded as a sync error",
                c.observeMainDataError().value,
            )
            collector.cancel()
        }
    }
}
