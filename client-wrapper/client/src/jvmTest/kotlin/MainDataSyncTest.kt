package qbittorrent

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MainDataSyncTest {
    private val serverState =
        """"server_state":{"alltime_dl":0,"alltime_ul":0,"average_time_queue":0,""" +
            """"connection_status":"connected","dht_nodes":0,"dl_info_data":0,"dl_info_speed":0,""" +
            """"dl_rate_limit":0,"free_space_on_disk":0,"global_ratio":"0","queued_io_jobs":0,""" +
            """"queueing":true,"read_cache_hits":"0","read_cache_overload":"0","refresh_interval":1500,""" +
            """"total_buffers_size":0,"total_peer_connections":0,"total_queued_size":0,""" +
            """"total_wasted_session":0,"up_info_data":0,"up_info_speed":0,"up_rate_limit":0,""" +
            """"use_alt_speed_limits":false,"write_cache_overload":"0"}"""

    private fun cat(name: String) = """"$name":{"name":"$name","savePath":""}"""

    private fun categoriesAfterSync(responses: (rid: String) -> String): Set<String> = runBlocking {
        val engine = MockEngine { request ->
            val rid = request.url.parameters["rid"].orEmpty()
            respond(
                responses(rid),
                headers =
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client =
            QBittorrentClient(
                baseUrl = "http://qbt",
                syncInterval = 10.milliseconds,
                httpClient = HttpClient(engine),
            )
        try {
            withTimeout(5_000) {
                client.observeMainData().map { it.categories.keys }.first { it.contains("new") }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `requests the next patch with the rid the server sent`() {
        // Any rid other than the one the server last sent gets an empty delta, the way a shared
        // API-key session answers a rid that was sent to another client.
        val categories = categoriesAfterSync { rid ->
            when (rid) {
                "0" ->
                    """{"rid":400041,"full_update":true,"categories":{${cat("old")}},$serverState}"""
                "400041" -> """{"rid":400042,"categories":{${cat("new")}}}"""
                else -> """{"rid":99}"""
            }
        }
        assertEquals(setOf("old", "new"), categories)
    }

    @Test
    fun `adds a category to a full update that had none`() {
        val categories = categoriesAfterSync { rid ->
            when (rid) {
                "0" -> """{"rid":1,"full_update":true,$serverState}"""
                "1" -> """{"rid":2,"full_update":true,$serverState}"""
                else -> """{"rid":3,"categories":{${cat("new")}}}"""
            }
        }
        assertEquals(setOf("new"), categories)
    }
}
