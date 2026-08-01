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

    const val RSS = "rss"

    const val RSS_ARTICLES = "rssArticles"
    const val ARG_RSS_ITEM_PATH = "itemPath"
    const val RSS_ARTICLES_PATTERN = "$RSS_ARTICLES/{$ARG_RSS_ITEM_PATH}"

    const val RSS_RULE_EDITOR = "rssRuleEditor"
    const val ARG_RSS_RULE_NAME = "ruleName"
    const val RSS_RULE_EDITOR_PATTERN = "$RSS_RULE_EDITOR?$ARG_RSS_RULE_NAME={$ARG_RSS_RULE_NAME}"

    fun config(serverId: Int = -1) = "$CONFIG?$ARG_SERVER_ID=$serverId"

    fun torrentDetails(hash: String) = "$TORRENT_DETAILS/$hash"

    // Item paths contain "\" separators and can contain spaces - percent-encode so the whole path
    // stays a single route segment. Uri.encode (not URLEncoder) matches how Navigation Compose
    // decodes path args internally (it uses Uri decoding, where "+" is literal, not a space).
    fun rssArticles(itemPath: String) = "$RSS_ARTICLES/${android.net.Uri.encode(itemPath)}"

    fun rssRuleEditor(ruleName: String? = null) =
        "$RSS_RULE_EDITOR?$ARG_RSS_RULE_NAME=${ruleName?.let(android.net.Uri::encode) ?: ""}"
}
