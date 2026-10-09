package dev.yashgarg.qbit.data

import com.github.michaelbull.result.get
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import qbittorrent.QBittorrentClient
import qbittorrent.models.AltSpeedSchedule
import qbittorrent.models.SchedulerDays

/**
 * The schedule is stored as qBittorrent's own preference keys: a `scheduler_days` ordinal and two
 * separate hour/minute pairs. The server only applies a time when both halves of a pair arrive
 * together, so the setter must always send them as a pair.
 */
class AltSpeedScheduleTest {

    /** Captures the `json=` form body of the last setPreferences call. */
    private var lastSetBody: String? = null

    private fun repository(prefsJson: String): QbitRepository {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/login") ->
                    respond("Ok.", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "SID=mock"))
                path.endsWith("/app/setPreferences") -> {
                    val raw =
                        when (val body = request.body) {
                            is TextContent -> body.text
                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            else -> ""
                        }
                    // setPreferences posts a form, so the JSON arrives percent-encoded in `json=`.
                    lastSetBody = URLDecoder.decode(raw.removePrefix("json="), "UTF-8")
                    respond("Ok.")
                }
                path.endsWith("/app/preferences") ->
                    respond(
                        prefsJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> respond("Ok.")
            }
        }
        val manager =
            object : ClientManager {
                private val status = MutableSharedFlow<ConfigStatus>()
                override val configStatus: SharedFlow<ConfigStatus> = status.asSharedFlow()

                override suspend fun checkAndGetClient() =
                    QBittorrentClient(
                        baseUrl = "http://localhost",
                        httpClient = HttpClient(engine),
                        dispatcher = Dispatchers.Default,
                    )

                override suspend fun setActiveServer(id: Int) = Unit
            }
        return QbitRepository(manager)
    }

    @Test
    fun `reads the schedule from the server preferences`() = runTest {
        val repo =
            repository(
                """{"scheduler_enabled":true,"schedule_from_hour":23,"schedule_from_min":30,
                   "schedule_to_hour":7,"schedule_to_min":5,"scheduler_days":1}"""
                    .replace("\n", "")
            )
        val schedule = repo.getAltSpeedSchedule().get()
        assertEquals(
            AltSpeedSchedule(
                enabled = true,
                fromMinutesOfDay = 23 * 60 + 30,
                toMinutesOfDay = 7 * 60 + 5,
                days = SchedulerDays.WEEKDAY,
            ),
            schedule,
        )
    }

    @Test
    fun `defaults to every day when the server reports an unknown day value`() = runTest {
        val repo = repository("""{"scheduler_enabled":false,"scheduler_days":99}""")
        assertEquals(SchedulerDays.EVERY_DAY, repo.getAltSpeedSchedule().get()?.days)
    }

    @Test
    fun `writes each time as an hour and minute pair`() = runTest {
        val repo = repository("{}")
        repo.setAltSpeedSchedule(
            AltSpeedSchedule(
                enabled = true,
                fromMinutesOfDay = 9 * 60 + 15,
                toMinutesOfDay = 18 * 60,
                days = SchedulerDays.WEEKEND,
            )
        )
        val body = lastSetBody.orEmpty()
        // Sending an hour without its minute is silently ignored by the server, so both must be
        // present for each end of the window.
        for (expected in
            listOf(
                "\"schedule_from_hour\":9",
                "\"schedule_from_min\":15",
                "\"schedule_to_hour\":18",
                "\"schedule_to_min\":0",
                "\"scheduler_days\":2",
                "\"scheduler_enabled\":true",
            )) {
            assertTrue("missing $expected in $body", body.contains(expected))
        }
    }

    @Test
    fun `a midnight to midnight window survives the round trip`() = runTest {
        val repo = repository("{}")
        repo.setAltSpeedSchedule(
            AltSpeedSchedule(
                enabled = true,
                fromMinutesOfDay = 0,
                toMinutesOfDay = 0,
                days = SchedulerDays.EVERY_DAY,
            )
        )
        val body = lastSetBody.orEmpty()
        assertTrue(body, body.contains("\"schedule_from_hour\":0"))
        assertTrue(body, body.contains("\"schedule_from_min\":0"))
        assertTrue(body, body.contains("\"scheduler_days\":0"))
    }
}
