package dev.yashgarg.qbit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.yashgarg.qbit.ui.server.StateFilter
import dev.yashgarg.qbit.ui.server.matchesFilter
import qbittorrent.models.GlobalTransferInfo
import qbittorrent.models.Torrent

/** What the widget last knew about the active server; one copy per placed widget. */
data class StatusWidgetState(
    val status: Status,
    val serverName: String?,
    val dlSpeed: Long,
    val upSpeed: Long,
    val counts: Counts?,
    val altSpeedLimits: Boolean,
    val busy: Boolean,
) {
    enum class Status {
        LOADING,
        CONNECTED,
        OFFLINE,
        NO_SERVER,
    }

    data class Counts(val downloading: Int, val seeding: Int, val stopped: Int)

    companion object {
        fun from(prefs: Preferences): StatusWidgetState =
            StatusWidgetState(
                status =
                    prefs[Keys.status]?.let { name -> Status.entries.find { it.name == name } }
                        ?: Status.LOADING,
                serverName = prefs[Keys.serverName],
                dlSpeed = prefs[Keys.dlSpeed] ?: 0L,
                upSpeed = prefs[Keys.upSpeed] ?: 0L,
                counts =
                    prefs[Keys.downloading]?.let {
                        Counts(it, prefs[Keys.seeding] ?: 0, prefs[Keys.stopped] ?: 0)
                    },
                altSpeedLimits = prefs[Keys.altSpeedLimits] ?: false,
                busy = prefs[Keys.busy] ?: false,
            )

        fun countsOf(torrents: List<Torrent>) =
            Counts(
                downloading = torrents.count { it.matchesFilter(StateFilter.DOWNLOADING) },
                seeding = torrents.count { it.matchesFilter(StateFilter.SEEDING) },
                stopped = torrents.count { it.matchesFilter(StateFilter.STOPPED) },
            )
    }

    internal object Keys {
        val status = stringPreferencesKey("status")
        val serverName = stringPreferencesKey("server_name")
        val dlSpeed = longPreferencesKey("dl_speed")
        val upSpeed = longPreferencesKey("up_speed")
        val downloading = intPreferencesKey("downloading")
        val seeding = intPreferencesKey("seeding")
        val stopped = intPreferencesKey("stopped")
        val altSpeedLimits = booleanPreferencesKey("alt_speed_limits")
        val busy = booleanPreferencesKey("busy")
    }
}

/** Writes new readings into every placed status widget and redraws them. */
object StatusWidgetUpdater {

    /** Cheap, synchronous check so pollers can skip widget work entirely when none is placed. */
    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(
                StatusWidgetReceiver::class.java,
                StatusWidgetSpeedsReceiver::class.java,
                StatusWidgetSquareReceiver::class.java,
            )
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    suspend fun showTransfer(
        context: Context,
        serverName: String?,
        info: GlobalTransferInfo,
        counts: StatusWidgetState.Counts?,
        altSpeedLimits: Boolean?,
    ) =
        update(context) {
            it[StatusWidgetState.Keys.status] = StatusWidgetState.Status.CONNECTED.name
            setServerName(it, serverName)
            it[StatusWidgetState.Keys.dlSpeed] = info.dlInfoSpeed
            it[StatusWidgetState.Keys.upSpeed] = info.upInfoSpeed
            // transfer/info doesn't report the mode, so it's fetched separately when known.
            if (altSpeedLimits != null) it[StatusWidgetState.Keys.altSpeedLimits] = altSpeedLimits
            // Without fresh counts keep the last ones rather than blanking the line.
            if (counts != null) {
                it[StatusWidgetState.Keys.downloading] = counts.downloading
                it[StatusWidgetState.Keys.seeding] = counts.seeding
                it[StatusWidgetState.Keys.stopped] = counts.stopped
            }
            it[StatusWidgetState.Keys.busy] = false
        }

    suspend fun showStatus(
        context: Context,
        status: StatusWidgetState.Status,
        serverName: String?,
    ) =
        update(context) {
            it[StatusWidgetState.Keys.status] = status.name
            setServerName(it, serverName)
            it[StatusWidgetState.Keys.busy] = false
        }

    suspend fun update(context: Context, block: (MutablePreferences) -> Unit) {
        val widget = StatusWidget()
        GlanceAppWidgetManager(context).getGlanceIds(StatusWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { block(it) }
            widget.update(context, id)
        }
    }

    private fun setServerName(prefs: MutablePreferences, serverName: String?) {
        if (serverName != null) prefs[StatusWidgetState.Keys.serverName] = serverName
        else prefs.remove(StatusWidgetState.Keys.serverName)
    }
}
