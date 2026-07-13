package dev.yashgarg.qbit.utils

import io.ktor.client.network.sockets.*
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import qbittorrent.QBittorrentException

class ClientConnectionError : Throwable("Failed to connect to server")

fun Throwable.friendlyMessage(fallback: String = "Unknown error"): String {
    val mapped = ExceptionHandler.mapException(this)
    // Unknown/unmapped throwable: show the generic fallback, never a raw exception message.
    if (mapped === this) return fallback
    return mapped.message?.substringBefore(" [")?.trim() ?: fallback
}

object ExceptionHandler {
    fun mapException(ex: Throwable): Throwable =
        when (ex) {
            is UninitializedPropertyAccessException -> ClientConnectionError()
            is SocketTimeoutException -> Exception("Connection timed out")
            is ConnectTimeoutException -> ClientConnectionError()
            is ConnectException -> Exception("Could not reach server — check address and port")
            is UnknownHostException -> Exception("Server not found — check the hostname")
            is SSLException ->
                Exception(
                    "SSL error — for a self-signed certificate, install it in Android settings"
                )
            is QBittorrentException ->
                when {
                    // qBittorrent returns 409 Conflict when the torrent is already in the list.
                    ex.response?.status?.value == 409 ||
                        ex.message.contains("conflict", ignoreCase = true) ->
                        Exception("Torrent already exists")
                    ex.response?.status?.value == 401 || ex.response?.status?.value == 403 ->
                        Exception("Authentication failed — check the username and password")
                    ex.cause is ConnectTimeoutException -> ClientConnectionError()
                    ex.cause is SocketTimeoutException -> Exception("Connection timed out")
                    else -> ex
                }
            is java.io.IOException -> ClientConnectionError()
            else -> ex
        }
}
