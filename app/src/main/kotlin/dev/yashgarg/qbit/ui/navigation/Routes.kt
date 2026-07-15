package dev.yashgarg.qbit.ui.navigation

/**
 * Compose-navigation route strings. Mirrors the destinations from the old `nav_graph.xml`; args
 * match the former Safe Args (serverId: Int default -1, torrentHash: String).
 */
object Routes {
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SERVER = "server"
    const val SETTINGS = "settings"
    const val VERSION = "version"
    const val LOGS = "logs"

    const val CONFIG = "config"
    const val ARG_SERVER_ID = "serverId"
    const val CONFIG_PATTERN = "$CONFIG?$ARG_SERVER_ID={$ARG_SERVER_ID}"

    const val TORRENT_DETAILS = "torrentDetails"
    const val ARG_TORRENT_HASH = "torrentHash"
    const val TORRENT_DETAILS_PATTERN = "$TORRENT_DETAILS/{$ARG_TORRENT_HASH}"

    fun config(serverId: Int = -1) = "$CONFIG?$ARG_SERVER_ID=$serverId"

    fun torrentDetails(hash: String) = "$TORRENT_DETAILS/$hash"
}
