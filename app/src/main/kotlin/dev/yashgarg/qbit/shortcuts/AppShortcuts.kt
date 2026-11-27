package dev.yashgarg.qbit.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.LocalizedContext

/**
 * The launcher's long-press shortcuts. Published at runtime rather than from a static
 * `shortcuts.xml`, whose intents would have to hard-code the package name (which differs for the
 * preview build), and so their labels follow the in-app language.
 */
object AppShortcuts {
    const val ACTION_ADD_TORRENT = "dev.yashgarg.qbit.action.ADD_TORRENT"
    const val ACTION_SEARCH = "dev.yashgarg.qbit.action.SEARCH"
    const val ACTION_RSS = "dev.yashgarg.qbit.action.RSS"

    fun isShortcut(action: String?): Boolean =
        action == ACTION_ADD_TORRENT || action == ACTION_SEARCH || action == ACTION_RSS

    fun publish(context: Context) {
        val strings = LocalizedContext.of(context)
        val shortcuts =
            listOf(
                    Triple(
                        ACTION_ADD_TORRENT,
                        CommonR.string.add_torrent_title,
                        R.drawable.ic_shortcut_add,
                    ),
                    Triple(
                        ACTION_SEARCH,
                        CommonR.string.search_title,
                        R.drawable.ic_shortcut_search,
                    ),
                    Triple(ACTION_RSS, CommonR.string.rss_title, R.drawable.ic_shortcut_rss),
                )
                .mapIndexed { rank, (action, label, icon) ->
                    ShortcutInfoCompat.Builder(context, action)
                        .setShortLabel(strings.getString(label))
                        .setIcon(IconCompat.createWithResource(context, icon))
                        .setIntent(Intent(action).setClass(context, MainActivity::class.java))
                        .setRank(rank)
                        .build()
                }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
