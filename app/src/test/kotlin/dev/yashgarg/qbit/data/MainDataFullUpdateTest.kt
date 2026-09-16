package dev.yashgarg.qbit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.*

/**
 * A sync response flagged `full_update` is a whole snapshot, not a delta. The server sends one
 * mid-stream whenever it stops recognising our rid, so merging it would keep torrents the server
 * has already dropped - removals only ever arrive via `torrents_removed`, which a snapshot omits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDataFullUpdateTest {

    private fun serverState() =
        """
        {"connection_status":"connected","dht_nodes":0,"dl_info_data":0,"dl_info_speed":0,
                   "dl_rate_limit":0,"up_info_data":0,"up_info_speed":0,"up_rate_limit":0,
                   "alltime_dl":0,"alltime_ul":0,"average_time_queue":0,"free_space_on_disk":0,
                   "global_ratio":"0","queued_io_jobs":0,"queueing":false,"read_cache_hits":"0",
                   "read_cache_overload":"0","refresh_interval":1500,"total_buffers_size":0,
                   "total_peer_connections":0,"total_queued_size":0,"total_wasted_session":0,
                   "write_cache_overload":"0","use_alt_speed_limits":false}
        """
            .trimIndent()
            .replace("\n", "")

    private fun torrent(hash: String) =
        """"$hash":{"added_on":0,"amount_left":0,"auto_tmm":false,"availability":1.0,
           "category":"","completed":0,"completion_on":0,"content_path":"","dl_limit":-1,
           "dlspeed":0,"downloaded":0,"downloaded_session":0,"eta":0,"f_l_piece_prio":false,
           "force_start":false,"hash":"$hash","last_activity":0,"magnet_uri":"","max_ratio":-1,
           "max_seeding_time":-1,"name":"T-$hash","num_complete":0,"num_incomplete":0,
           "num_leechs":0,"num_seeds":0,"priority":0,"progress":1.0,"ratio":1.0,
           "ratio_limit":-1,"save_path":"","seeding_time_limit":-1,"seen_complete":0,
           "seq_dl":false,"size":1000,"state":"uploading","super_seeding":false,"tags":"",
           "time_active":0,"seeding_time":0,"total_size":1000,"tracker":"","up_limit":-1,
           "uploaded":0,"uploaded_session":0,"upspeed":0}"""
            .trimIndent()
            .replace("\n", "")

    private fun maindata(rid: Int, fullUpdate: Boolean, hashes: List<String>) =
        """{"rid":$rid,"full_update":$fullUpdate,
           "torrents":{${hashes.joinToString(",") { torrent(it) }}},
           "server_state":${serverState()}}"""
            .trimIndent()
            .replace("\n", "")

    /** Serves the given maindata bodies in order, one per sync poll. */
    private fun client(vararg bodies: String): QBittorrentClient {
        var call = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/login") ->
                    respond("Ok.", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "SID=mock"))
                path.endsWith("/sync/maindata") -> {
                    val body = bodies[minOf(call, bodies.lastIndex)]
                    call++
                    respond(
                        body,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond("Ok.")
            }
        }
        return QBittorrentClient(
            baseUrl = "http://localhost",
            httpClient = HttpClient(engine),
            syncInterval = kotlin.time.Duration.ZERO,
            dispatcher = Dispatchers.Default,
        )
    }

    @Test
    fun `a full update replaces torrents instead of merging them`() = runTest {
        // Snapshot with two torrents, then a snapshot where the server has dropped one. No
        // torrents_removed, exactly as a real full_update snapshot arrives.
        val c =
            client(
                maindata(1, fullUpdate = true, hashes = listOf("aaa", "bbb")),
                maindata(2, fullUpdate = true, hashes = listOf("aaa")),
            )
        try {
            val updates = c.observeMainData().take(2).toList()
            assertEquals(setOf("aaa", "bbb"), updates[0].torrents.keys)
            assertEquals(setOf("aaa"), updates[1].torrents.keys)
        } finally {
            c.close()
        }
    }

    @Test
    fun `a delta still merges new torrents into the existing set`() = runTest {
        val c =
            client(
                maindata(1, fullUpdate = true, hashes = listOf("aaa")),
                maindata(2, fullUpdate = false, hashes = listOf("bbb")),
            )
        try {
            val updates = c.observeMainData().take(2).toList()
            assertEquals(setOf("aaa"), updates[0].torrents.keys)
            assertEquals(setOf("aaa", "bbb"), updates[1].torrents.keys)
        } finally {
            c.close()
        }
    }

    @Test
    fun `a full update with no torrents clears the list`() = runTest {
        val c =
            client(
                maindata(1, fullUpdate = true, hashes = listOf("aaa", "bbb")),
                maindata(2, fullUpdate = true, hashes = emptyList()),
            )
        try {
            val updates = c.observeMainData().take(2).toList()
            assertTrue(updates[0].torrents.isNotEmpty())
            assertTrue(updates[1].torrents.isEmpty())
        } finally {
            c.close()
        }
    }

    @Test
    fun `the first snapshot is delivered as-is`() = runTest {
        val c = client(maindata(1, fullUpdate = true, hashes = listOf("aaa")))
        try {
            assertEquals(setOf("aaa"), c.observeMainData().first().torrents.keys)
        } finally {
            c.close()
        }
    }
}
