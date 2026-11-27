package dev.yashgarg.qbit.widget

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.activeServer
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.utils.LocalizedContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import qbittorrent.QBittorrentClient
import qbittorrent.getGlobalTransferInfo
import qbittorrent.getSpeedLimitsMode
import qbittorrent.getTorrents
import qbittorrent.pauseTorrents
import qbittorrent.resumeTorrents
import qbittorrent.toggleSpeedLimitsMode

/**
 * Runs a widget button's action against the active server, then refreshes every placed widget. With
 * no action it only refreshes, which is also what the periodic schedule does.
 */
@HiltWorker
class StatusWidgetWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val clientManager: ClientManager,
    private val prefsStore: DataStore<AppPreferences>,
    private val configDao: ConfigDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!StatusWidgetUpdater.hasWidgets(applicationContext)) return Result.success()
        val prefs = prefsStore.data.first()
        val server = configDao.getConfigs().first().activeServer(prefs.activeServerId)
        if (server == null) {
            StatusWidgetUpdater.showStatus(
                applicationContext,
                StatusWidgetState.Status.NO_SERVER,
                null,
            )
            return Result.success()
        }
        val client = clientManager.checkAndGetClient()
        if (client == null) {
            StatusWidgetUpdater.showStatus(
                applicationContext,
                StatusWidgetState.Status.OFFLINE,
                server.serverName,
            )
            return Result.success()
        }
        try {
            when (inputData.getString(KEY_ACTION)?.let(Action::valueOf)) {
                Action.PAUSE_ALL -> client.pauseTorrents()
                Action.RESUME_ALL -> client.resumeTorrents()
                Action.TOGGLE_ALT_SPEED -> client.toggleSpeedLimitsMode()
                null -> Unit
            }
            refresh(applicationContext, client, server.serverName, withCounts = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StatusWidgetUpdater.showStatus(
                applicationContext,
                StatusWidgetState.Status.OFFLINE,
                server.serverName,
            )
        }
        return Result.success()
    }

    // Expedited work needs this below Android 12, where it runs as a short foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val localized = LocalizedContext.of(applicationContext)
        val notification =
            AppNotificationManager.createNotification(
                applicationContext,
                localized.getString(CommonR.string.app_name),
                localized.getString(CommonR.string.widget_updating),
                R.drawable.ic_stat_qbit,
                persistent = true,
                channelId = applicationContext.getString(CommonR.string.monitor_channel_id),
                priority = NotificationCompat.PRIORITY_MIN,
            )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    enum class Action {
        PAUSE_ALL,
        RESUME_ALL,
        TOGGLE_ALT_SPEED,
    }

    companion object {
        private const val KEY_ACTION = "action"
        private const val NOW_WORK = "status_widget_now"
        private const val PERIODIC_WORK = "status_widget_periodic"
        // Clear of the status monitor (1) and the hash-derived event alert ids.
        private const val NOTIFICATION_ID = 0x7769

        suspend fun refresh(
            context: Context,
            client: QBittorrentClient,
            serverName: String?,
            withCounts: Boolean,
        ) {
            val info = client.getGlobalTransferInfo()
            val counts = if (withCounts) StatusWidgetState.countsOf(client.getTorrents()) else null
            val altSpeedLimits = client.getSpeedLimitsMode() == 1
            StatusWidgetUpdater.showTransfer(context, serverName, info, counts, altSpeedLimits)
        }

        /** Runs [action] (or just a refresh) right away; queued presses run in order. */
        fun runNow(context: Context, action: Action? = null) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NOW_WORK,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<StatusWidgetWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(workDataOf(KEY_ACTION to action?.name))
                        .build(),
                )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<StatusWidgetWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }
    }
}
