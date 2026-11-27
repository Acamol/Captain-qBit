package dev.yashgarg.qbit.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.LocalizedContext

object EventNotifications {
    const val EXTRA_NOTIFICATION_ID = "event_notification_id"
    private const val EXTRA_CLEAR_GROUP = "clear_event_notifications"
    private const val GROUP_KEY = "qbit.events"
    private const val SUMMARY_TAG = "event_summary"
    private const val SUMMARY_ID = 0

    @Synchronized
    fun post(
        context: Context,
        id: Int,
        title: String,
        content: String,
        contentIntent: PendingIntent,
        quiet: Boolean,
    ) {
        if (!AppNotificationManager.notificationsEnabled(context)) return
        val notification =
            AppNotificationManager.createEventNotification(
                context,
                title,
                content,
                R.drawable.ic_stat_qbit,
                contentIntent,
                quiet,
                groupKey = GROUP_KEY,
                deleteIntent = deleteIntent(context, id),
            )
        AppNotificationManager.sendNotification(context, id, notification)
        updateSummary(context, excludedId = id, posted = notification)
    }

    @Synchronized
    fun handleInteraction(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        if (intent.getBooleanExtra(EXTRA_CLEAR_GROUP, false)) {
            activeEvents(context).forEach { manager.cancel(it.id) }
            manager.cancel(SUMMARY_TAG, SUMMARY_ID)
        } else if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
            val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            activeEvents(context).filter { it.id == id }.forEach { manager.cancel(it.id) }
            updateSummary(context, excludedId = id)
        }
    }

    private fun activeEvents(context: Context) =
        context.getSystemService(NotificationManager::class.java).activeNotifications.filter {
            it.notification.group == GROUP_KEY &&
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }

    private fun updateSummary(
        context: Context,
        excludedId: Int,
        posted: Notification? = null,
    ) {
        val children =
            activeEvents(context).filter { it.id != excludedId }.map { it.notification } +
                listOfNotNull(posted)
        val manager = NotificationManagerCompat.from(context)
        if (children.isEmpty()) {
            manager.cancel(SUMMARY_TAG, SUMMARY_ID)
            return
        }

        val localized = LocalizedContext.of(context)
        val title = localized.getString(CommonR.string.events_channel_name)
        val latest = children.maxBy { it.`when` }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        children
            .sortedByDescending { it.`when` }
            .take(5)
            .forEach {
                style.addLine(
                    "${it.extras.getCharSequence(Notification.EXTRA_TITLE)}: " +
                        it.extras.getCharSequence(Notification.EXTRA_TEXT),
                )
            }
        val openIntent =
            Intent(context, EventNotificationOpenActivity::class.java).apply {
                action = "${context.packageName}.notifications.OPEN_GROUP"
                putExtra(EXTRA_CLEAR_GROUP, true)
            }
        val summary =
            NotificationCompat.Builder(
                    context,
                    latest.channelId,
                )
                .setSmallIcon(R.drawable.ic_stat_qbit)
                .setContentTitle(title)
                .setContentText(latest.extras.getCharSequence(Notification.EXTRA_TEXT))
                .setStyle(style)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        SUMMARY_ID,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setDeleteIntent(deleteIntent(context))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
        AppNotificationManager.sendNotification(context, SUMMARY_ID, summary, tag = SUMMARY_TAG)
    }

    private fun deleteIntent(context: Context, id: Int? = null): PendingIntent {
        val intent =
            Intent(context, EventNotificationDismissReceiver::class.java).apply {
                action = "${context.packageName}.notifications.DISMISS.${id ?: "GROUP"}"
                if (id == null) putExtra(EXTRA_CLEAR_GROUP, true)
                else putExtra(EXTRA_NOTIFICATION_ID, id)
            }
        return PendingIntent.getBroadcast(
            context,
            id ?: SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
