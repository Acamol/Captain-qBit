package dev.yashgarg.qbit.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.utils.toHumanReadable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import qbittorrent.getGlobalTransferInfo
import qbittorrent.getTorrents
import qbittorrent.models.Torrent

@HiltWorker
class StatusWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val clientManager: ClientManager,
    private val prefsStore: DataStore<ServerPreferences>,
) : CoroutineWorker(appContext, workerParams) {

    // Lets WorkManager start this as an expedited foreground service right away. Pick the channel
    // up front from the user's prefs so an events-only user never even briefly sees the louder
    // status notification.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        if (prefsStore.data.first().statusNotification) {
            createForegroundInfo("Connecting…", "")
        } else {
            createForegroundInfo("Captain qBit", "Torrent alerts are on", minimal = true)
        }

    override suspend fun doWork(): Result {
        // Promote to a foreground service immediately, while the app is still likely in the
        // foreground. If we waited until the first data emission (which needs a server round
        // trip), the user could background the app first and Android 12+ would then block the
        // foreground-service start, so the notification would never appear.
        setForeground(getForegroundInfo())
        getStatus()
        return Result.success()
    }

    // Polls on a single interval to serve both the ongoing status notification and torrent-event
    // alerts (completed / checked). The user can toggle each independently in Settings; the service
    // keeps running while any of them is enabled, and stops itself once all are off.
    private suspend fun getStatus() {
        while (true) {
            val prefs = prefsStore.data.first()
            val eventsOn = prefs.notifyOnComplete || prefs.notifyOnChecked
            if (!prefs.statusNotification && !eventsOn) return

            // Fetch the client each tick so the worker follows a server switch (setActiveServer
            // rebuilds it); a captured reference would keep polling the old server.
            val client = clientManager.checkAndGetClient()
            if (client == null) {
                delay(REFRESH_INTERVAL_MS)
                continue
            }

            try {
                if (prefs.statusNotification) {
                    val info = client.getGlobalTransferInfo()
                    setForeground(
                        createForegroundInfo(
                            "Server State • Connected",
                            "DL: ${info.dlInfoSpeed.toHumanReadable()}/s | UL: ${info.upInfoSpeed.toHumanReadable()}/s",
                        )
                    )
                } else {
                    // Events-only: Android still requires an ongoing notification for the service,
                    // so show a minimal, min-importance one (no sound/status-bar icon) instead of
                    // the speed readout.
                    setForeground(
                        createForegroundInfo(
                            "Captain qBit",
                            "Torrent alerts are on",
                            minimal = true,
                        )
                    )
                }

                if (eventsOn) {
                    val torrents = client.getTorrents()
                    if (prefs.notifyOnComplete) notifyCompletions(torrents, prefs)
                    if (prefs.notifyOnChecked) notifyRechecks(torrents, prefs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient error — keep the last notification and retry on the next tick.
            }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Fire a "download complete" alert for any torrent whose completion is newer than the
     * per-server watermark, then advance (and persist) the watermark. Using qBittorrent's own
     * `completion_on` timestamp — rather than diffing progress across our polls — means completions
     * that happen while this worker isn't running are still caught on the next poll, and rechecks
     * (which don't change completion_on) never re-alert. The watermark is written only when it
     * moves.
     */
    private suspend fun notifyCompletions(torrents: List<Torrent>, prefs: ServerPreferences) {
        val serverId = prefs.activeServerId
        // Only actually-complete torrents carry a real completion_on; incomplete ones report -1.
        val completed = torrents.filter { it.progress >= 1f && it.completedOn > 0 }
        val newestCompletion = completed.maxOfOrNull { it.completedOn } ?: return

        val watermark = prefs.notifCompletionSeen[serverId]
        if (watermark == null) {
            // First time watching this server: adopt current completions as the baseline silently.
            persistCompletionWatermark(serverId, newestCompletion)
            return
        }

        completed
            .filter { it.completedOn > watermark }
            .forEach { notifyEvent("complete:${it.hash}".hashCode(), "Download complete", it.name) }
        if (newestCompletion > watermark) persistCompletionWatermark(serverId, newestCompletion)
    }

    private suspend fun persistCompletionWatermark(serverId: Int, value: Long) {
        prefsStore.updateData {
            it.copy(notifCompletionSeen = it.notifCompletionSeen + (serverId to value))
        }
    }

    /**
     * Fire a "check complete" alert for any torrent that was being checked as of the last poll but
     * no longer is. The set of currently-checking hashes is persisted per server (like the
     * completion watermark), so this survives the worker restarting and never needs a live
     * in-memory baseline. A torrent already done when we first watch is never in the set, so it
     * doesn't alert; one caught mid-check is recorded and alerts once it finishes.
     */
    private suspend fun notifyRechecks(torrents: List<Torrent>, prefs: ServerPreferences) {
        val serverId = prefs.activeServerId
        val byHash = torrents.associateBy(Torrent::hash)
        val nowChecking = torrents.filter { it.state.isChecking() }.map(Torrent::hash).toSet()
        val wasChecking = prefs.notifCheckingSeen[serverId] ?: emptySet()

        wasChecking.forEach { hash ->
            if (hash !in nowChecking) {
                byHash[hash]?.let {
                    notifyEvent("checked:$hash".hashCode(), "Check complete", it.name)
                }
            }
        }
        if (nowChecking != wasChecking) {
            prefsStore.updateData {
                it.copy(notifCheckingSeen = it.notifCheckingSeen + (serverId to nowChecking))
            }
        }
    }

    private fun notifyEvent(id: Int, title: String, content: String) {
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(applicationContext, id, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification =
            AppNotificationManager.createEventNotification(
                applicationContext,
                title,
                content,
                R.drawable.ic_stat_qbit,
                pendingIntent,
            )
        AppNotificationManager.sendNotification(applicationContext, id, notification)
    }

    private fun Torrent.State.isChecking(): Boolean =
        this == Torrent.State.CHECKING_UP ||
            this == Torrent.State.CHECKING_DL ||
            this == Torrent.State.CHECKING_RESUME_DATA

    private fun createForegroundInfo(
        title: String,
        text: String,
        minimal: Boolean = false,
    ): ForegroundInfo {
        val closeIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val channelId =
            applicationContext.getString(
                if (minimal) CommonR.string.monitor_channel_id else CommonR.string.status_channel_id
            )
        val notification =
            AppNotificationManager.createNotification(
                applicationContext,
                title,
                text,
                R.drawable.ic_stat_qbit,
                persistent = true,
                actions = listOf(Action(null, "Close", closeIntent)),
                contentIntent = pendingIntent,
                channelId = channelId,
                priority =
                    if (minimal) NotificationCompat.PRIORITY_MIN
                    else NotificationCompat.PRIORITY_LOW,
            )
        // Android 10+ requires declaring a foreground service type for setForeground(). From
        // Android 14 we use specialUse: this monitor runs as long as the user keeps it enabled,
        // and Android 15 force-stops dataSync services after 6 hours.
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(1, notification)
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val WORK_TAG = "status_update"

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * (Re)start the monitoring service. REPLACE resets only the in-memory recheck baseline;
         * completion alerts use the persisted watermark, so they aren't lost across a restart.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_TAG,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<StatusWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints)
                        .addTag(WORK_TAG)
                        .build(),
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
