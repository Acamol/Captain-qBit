package dev.yashgarg.qbit.data.manager

import android.annotation.SuppressLint
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captures the certificate chain a server presents during a TLS handshake, without validating it
 * and without sending any HTTP request - this exists solely so the "untrusted certificate" dialog
 * can show the user a real fingerprint to review before they decide to trust it. It is never used
 * to talk to the qBittorrent API: the real traffic client ([ClientManager.httpClient]) always
 * validates fully via `HandshakeCertificates` (system CAs, plus any certificate the user has
 * already explicitly approved for that one server). The accept-anything [X509TrustManager] below is
 * scoped to this one throwaway socket only and is never handed to that real client.
 */
object CertificateProbe {
    private const val HANDSHAKE_TIMEOUT_MS = 5000

    // Lint's CustomX509TrustManager check exists because this exact pattern is commonly misused
    // to accidentally disable validation for real traffic. Here it's safe: the accept-anything
    // TrustManager below is scoped to one throwaway diagnostic socket that sends zero bytes and
    // is never handed to ClientManager.httpClient's real traffic client - see the class doc.
    @SuppressLint("CustomX509TrustManager")
    suspend fun fetchPresentedCertificate(
        host: String,
        port: Int,
    ): Result<X509Certificate, Throwable> = runSuspendCatching {
        withContext(Dispatchers.IO) {
            var chain: Array<out X509Certificate>? = null
            val captureTrustManager =
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        c: Array<out X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun checkServerTrusted(
                        c: Array<out X509Certificate>,
                        authType: String,
                    ) {
                        chain = c
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            val sslContext =
                SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(captureTrustManager), null)
                }
            (sslContext.socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                socket.sslParameters =
                    socket.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
                socket.startHandshake()
            }
            requireNotNull(chain?.firstOrNull()) { "Server presented no certificate" }
        }
    }
}
