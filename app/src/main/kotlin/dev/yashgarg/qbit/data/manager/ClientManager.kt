package dev.yashgarg.qbit.data.manager

import android.util.Base64
import android.util.Log
import dev.yashgarg.qbit.data.models.ConfigStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharedFlow
import qbittorrent.QBittorrentClient

interface ClientManager {
    val configStatus: SharedFlow<ConfigStatus>

    suspend fun checkAndGetClient(): QBittorrentClient?

    /** Persist the active server id; the client is rebuilt for it on the next request. */
    suspend fun setActiveServer(id: Int)

    companion object {
        const val tag = "ClientManager"
        val syncInterval = 5.seconds

        fun httpClient(basicAuthCredentials: Pair<String, String>? = null): HttpClient {
            return HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 3000
                    // Without a socket timeout, a request reusing a keep-alive connection that died
                    // during a network handoff (Wi-Fi<->mobile) hangs on the read instead of the
                    // connect, stalling polling. Failing fast lets OkHttp retry on a fresh
                    // connection; requestTimeout caps the whole call as a backstop.
                    socketTimeoutMillis = 10_000
                    requestTimeoutMillis = 20_000
                }
                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                Log.i(tag, message)
                            }
                        }
                    level = LogLevel.NONE
                }
                if (basicAuthCredentials != null) {
                    val encoded =
                        Base64.encodeToString(
                            "${basicAuthCredentials.first}:${basicAuthCredentials.second}"
                                .toByteArray(),
                            Base64.NO_WRAP,
                        )
                    defaultRequest { header(HttpHeaders.Authorization, "Basic $encoded") }
                }
            }
        }
    }
}
