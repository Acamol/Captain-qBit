package dev.yashgarg.qbit.utils

import dev.yashgarg.qbit.common.R as CommonR
import io.ktor.client.network.sockets.*
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import qbittorrent.QBittorrentException

/** Broad classification of a caught [Throwable], independent of how it's ultimately worded. */
private enum class ErrorKind {
    CONNECTION_FAILED,
    CONNECTION_TIMED_OUT,
    SERVER_UNREACHABLE,
    HOST_NOT_FOUND,
    SSL_ERROR,
    SSL_UNTRUSTED_CERTIFICATE,
    TORRENT_ALREADY_EXISTS,
    AUTHENTICATION_FAILED,
    UNKNOWN,
}

/**
 * Resolves [this] to a user-facing message via [getString] (e.g. `Context::getString` or a
 * ViewModel's own resource-resolving helper), or [fallback] when the throwable doesn't map to any
 * known kind.
 */
fun Throwable.friendlyMessage(
    getString: (Int) -> String,
    fallback: String = getString(CommonR.string.unknown_error),
): String =
    when (ExceptionHandler.classify(this)) {
        ErrorKind.CONNECTION_FAILED -> getString(CommonR.string.error_connection_failed)
        ErrorKind.CONNECTION_TIMED_OUT -> getString(CommonR.string.error_connection_timed_out)
        ErrorKind.SERVER_UNREACHABLE -> getString(CommonR.string.error_server_unreachable)
        ErrorKind.HOST_NOT_FOUND -> getString(CommonR.string.error_host_not_found)
        ErrorKind.SSL_ERROR -> getString(CommonR.string.error_ssl)
        ErrorKind.SSL_UNTRUSTED_CERTIFICATE ->
            getString(CommonR.string.error_ssl_untrusted_certificate)
        ErrorKind.TORRENT_ALREADY_EXISTS -> getString(CommonR.string.error_torrent_already_exists)
        ErrorKind.AUTHENTICATION_FAILED -> getString(CommonR.string.error_authentication_failed)
        ErrorKind.UNKNOWN -> fallback
    }

/** True when [this] is specifically an untrusted-CA chain failure, not any other SSL error. */
fun Throwable.isUntrustedCertificateError(): Boolean =
    ExceptionHandler.classify(this) == ErrorKind.SSL_UNTRUSTED_CERTIFICATE

private object ExceptionHandler {
    fun classify(ex: Throwable): ErrorKind =
        when (ex) {
            is UninitializedPropertyAccessException -> ErrorKind.CONNECTION_FAILED
            is SocketTimeoutException -> ErrorKind.CONNECTION_TIMED_OUT
            is ConnectTimeoutException -> ErrorKind.CONNECTION_FAILED
            is ConnectException -> ErrorKind.SERVER_UNREACHABLE
            is UnknownHostException -> ErrorKind.HOST_NOT_FOUND
            is SSLHandshakeException ->
                if (
                    ex.cause is CertPathValidatorException ||
                        ex.cause?.cause is CertPathValidatorException ||
                        ex.message?.contains("PKIX path", ignoreCase = true) == true
                ) {
                    ErrorKind.SSL_UNTRUSTED_CERTIFICATE
                } else ErrorKind.SSL_ERROR
            is SSLException -> ErrorKind.SSL_ERROR
            is QBittorrentException ->
                when {
                    // qBittorrent returns 409 Conflict when the torrent is already in the list.
                    ex.response?.status?.value == 409 ||
                        ex.message.contains("conflict", ignoreCase = true) ->
                        ErrorKind.TORRENT_ALREADY_EXISTS
                    ex.response?.status?.value == 401 || ex.response?.status?.value == 403 ->
                        ErrorKind.AUTHENTICATION_FAILED
                    // Socket errors reach us wrapped as the cause (unreachable host, DNS,
                    // timeout, SSL, …). Classify the underlying cause so the user gets a specific
                    // hint instead of the generic fallback.
                    ex.cause != null && ex.cause !== ex -> classify(ex.cause!!)
                    else -> ErrorKind.UNKNOWN
                }
            is java.io.IOException -> ErrorKind.CONNECTION_FAILED
            else -> ErrorKind.UNKNOWN
        }
}
