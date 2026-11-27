package dev.yashgarg.qbit.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.yashgarg.qbit.MainActivity

/** Only the app's notification PendingIntents may acknowledge events when opening the UI. */
class EventNotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            EventNotifications.handleInteraction(this, intent)
            val open =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    for (key in
                        listOf(MainActivity.EXTRA_TORRENT_HASH, MainActivity.EXTRA_RSS_ITEM_PATH)) {
                        this@EventNotificationOpenActivity.intent.getStringExtra(key)?.let {
                            putExtra(key, it)
                        }
                    }
                }
            startActivity(open)
        }
        finish()
    }
}
