package dev.yashgarg.qbit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EventNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventNotifications.handleInteraction(context, intent)
    }
}
