package dev.yashgarg.qbit.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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

    // Lets WorkManager start this as an expedited foreground service right away.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Connecting…", "")

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
        val client = clientManager.checkAndGetClient() ?: return
        // Baseline of the previous poll, keyed by hash, used to detect state transitions. Null
        // until the first successful torrent fetch so we never alert for the pre-existing state.
        var previous: Map<String, Torrent>? = null

        while (true) {
            val prefs = prefsStore.data.first()
            val eventsOn = prefs.notifyOnComplete || prefs.notifyOnChecked
            if (!prefs.statusNotification && !eventsOn) return

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
                    // so show a low-key one instead of the speed readout.
                    setForeground(
                        createForegroundInfo("Captain qBit", "Watching for torrent events")
                    )
                }

                if (eventsOn) {
                    val current = client.getTorrents().associateBy(Torrent::hash)
                    detectEvents(previous, current, prefs)
                    previous = current
                } else {
                    previous = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient error — keep the last notification and retry on the next tick.
            }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun detectEvents(
        previous: Map<String, Torrent>?,
        current: Map<String, Torrent>,
        prefs: ServerPreferences,
    ) {
        // First poll (or right after events were toggled on): just establish the baseline.
        if (previous == null) return

        current.values.forEach { torrent ->
            val before = previous[torrent.hash] ?: return@forEach

            // Progress climbing to 1.0 signals a finished download — but during a recheck the
            // reported progress also climbs back to 1.0 as pieces are verified. A real completion
            // happens while downloading, so ignore the crossing if the torrent was checking.
            if (
                prefs.notifyOnComplete &&
                    !before.state.isChecking() &&
                    before.progress < 1f &&
                    torrent.progress >= 1f
            ) {
                notifyEvent(
                    "complete:${torrent.hash}".hashCode(),
                    "Download complete",
                    torrent.name
                )
            }
            if (prefs.notifyOnChecked && before.state.isChecking() && !torrent.state.isChecking()) {
                notifyEvent("checked:${torrent.hash}".hashCode(), "Check complete", torrent.name)
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

    private fun createForegroundInfo(title: String, text: String): ForegroundInfo {
        val closeIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification =
            AppNotificationManager.createNotification(
                applicationContext,
                title,
                text,
                R.drawable.ic_stat_qbit,
                true,
                listOf(Action(null, "Close", closeIntent)),
                pendingIntent
            )
        // Android 10+ requires declaring a foreground service type for setForeground().
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, notification)
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val WORK_TAG = "status_update"

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * (Re)start the monitoring service. REPLACE resets the baseline so no stale alerts fire.
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
