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
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import qbittorrent.QBittorrentClient

interface ClientManager {
    val configStatus: SharedFlow<ConfigStatus>

    suspend fun checkAndGetClient(): QBittorrentClient?

    /** Persist the active server id; the client is rebuilt for it on the next request. */
    suspend fun setActiveServer(id: Int)

    companion object {
        const val tag = "ClientManager"

        fun httpClient(
            basicAuthCredentials: Pair<String, String>? = null,
            pinnedCertificateDer: ByteArray? = null,
        ): HttpClient {
            return HttpClient(OkHttp) {
                engine { preconfigured = buildOkHttpClient(pinnedCertificateDer) }
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

        /**
         * Builds the OkHttp client actually used for qBittorrent API traffic. Certificate trust is
         * always full chain validation against the platform's system CAs; a per-server pinned
         * certificate (approved once by the user via the "untrusted certificate" dialog, see
         * CertificateProbe) is a single additional trust anchor scoped to that request, never a
         * replacement for normal validation - there is no trust-all/bypass TrustManager here.
         */
        private fun buildOkHttpClient(pinnedCertificateDer: ByteArray?): OkHttpClient {
            val handshakeCertificates =
                HandshakeCertificates.Builder()
                    .addPlatformTrustedCertificates()
                    .apply {
                        pinnedCertificateDer?.let { der ->
                            addTrustedCertificate(parseCertificate(der))
                        }
                    }
                    .build()
            return OkHttpClient.Builder()
                .sslSocketFactory(
                    handshakeCertificates.sslSocketFactory(),
                    handshakeCertificates.trustManager,
                )
                .build()
        }

        private fun parseCertificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream())
                as X509Certificate
    }
}
