package dev.yashgarg.qbit.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat.Action
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.utils.toHumanReadable

@HiltWorker
class StatusWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val clientManager: ClientManager,
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

    private suspend fun getStatus() {
        val client = clientManager.checkAndGetClient()
        client?.observeMainData()?.collect { data ->
            val state = data.serverState
            setForeground(
                createForegroundInfo(
                    "Server State • Connected",
                    "DL: ${state.dlInfoSpeed.toHumanReadable()}/s | UL: ${state.upInfoSpeed.toHumanReadable()}/s",
                )
            )
        }
    }

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
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
