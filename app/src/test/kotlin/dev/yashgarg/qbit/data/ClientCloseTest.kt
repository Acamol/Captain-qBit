package dev.yashgarg.qbit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import qbittorrent.QBittorrentClient

/**
 * The client is rebuilt on every server or sync-interval change, so closing one has to release its
 * engine: an OkHttp engine holds a connection pool and dispatcher threads.
 */
class ClientCloseTest {
    @Test
    fun `closing the client closes the engine of the HttpClient it was given`() = runBlocking {
        // Built from the engine factory, as the app's OkHttp client is: an HttpClient only closes
        // an engine it created itself.
        val http = HttpClient(MockEngine) { engine { addHandler { respondOk() } } }
        val engine = http.engine
        val client = QBittorrentClient("http://localhost", httpClient = http)

        client.close()

        assertNotNull(
            "engine still open after close()",
            withTimeoutOrNull(5_000) { engine.coroutineContext[Job]!!.join() },
        )
    }
}
