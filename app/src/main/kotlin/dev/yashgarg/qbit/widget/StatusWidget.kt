package dev.yashgarg.qbit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders as GlanceColors
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.ui.theme.brandColorScheme
import dev.yashgarg.qbit.utils.LocalizedContext
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.widget.StatusWidgetState.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: the active server's speeds and torrent counts, with quick controls. */
/**
 * @param previewLayout the layout the picker preview shows. Each picker entry pins its own: left to
 *   choose by size, launchers (One UI) render the preview smaller than a placed widget and drop to
 *   a sparser layout.
 */
class StatusWidget(private val previewLayout: DpSize = LARGE) : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = colorsFor(context)
        // The widget's own context ignores the in-app language below Android 13.
        val strings = LocalizedContext.of(context)
        provideContent {
            GlanceTheme(colors = colors ?: GlanceTheme.colors) {
                Content(StatusWidgetState.from(currentState<Preferences>()), strings)
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val colors = colorsFor(context)
        val strings = LocalizedContext.of(context)
        provideContent {
            GlanceTheme(colors = colors ?: GlanceTheme.colors) {
                Content(PREVIEW_STATE, strings, previewLayout)
            }
        }
    }

    /** The app's palette choice: wallpaper colors (null here) when it uses them, else the brand. */
    private suspend fun colorsFor(context: Context): GlanceColors? {
        val prefs =
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                .prefsStore()
                .data
                .first()
        return if (prefs.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null
        else ColorProviders(light = brandScheme(context, false), dark = brandScheme(context, true))
    }

    private fun brandScheme(context: Context, dark: Boolean) =
        context
            .createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    uiMode =
                        (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO
                },
            )
            .let { themed -> brandColorScheme(dark) { Color(themed.getColor(it)) } }

    companion object {
        // Speeds only; stacked speeds and controls (2x2); speeds and controls; and everything,
        // with the server name and counts.
        internal val SMALL = DpSize(120.dp, 56.dp)
        internal val NARROW = DpSize(110.dp, 150.dp)
        internal val MEDIUM = DpSize(200.dp, 120.dp)
        internal val LARGE = DpSize(250.dp, 170.dp)
        private val SIZES = setOf(SMALL, NARROW, MEDIUM, LARGE)

        private val PREVIEW_STATE =
            StatusWidgetState(
                status = Status.CONNECTED,
                serverName = null,
                dlSpeed = 12_400_000,
                upSpeed = 1_850_000,
                counts = StatusWidgetState.Counts(downloading = 3, seeding = 12, stopped = 2),
                altSpeedLimits = false,
                busy = false,
            )

        /** Hands the launcher rendered previews; Android 15+ only. */
        suspend fun publishPreviews(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
            val manager = GlanceAppWidgetManager(context)
            val providers =
                AppWidgetManager.getInstance(context)
                    .getInstalledProvidersForPackage(
                        context.packageName,
                        null,
                    )
            listOf(
                    StatusWidgetReceiver::class,
                    StatusWidgetSpeedsReceiver::class,
                    StatusWidgetSquareReceiver::class,
                )
                .filter { receiver ->
                    // Only when missing (an app update clears them): the calls are rate-limited.
                    providers
                        .firstOrNull { it.provider.className == receiver.java.name }
                        ?.generatedPreviewCategories == 0
                }
                .forEach { runCatching { manager.setWidgetPreviews(it) } }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prefsStore(): DataStore<AppPreferences>
    }
}

@Composable
private fun Content(
    state: StatusWidgetState,
    strings: Context,
    // Responsive mode renders once per declared size, so this is always one of them.
    size: DpSize = LocalSize.current,
) {
    val large = size == StatusWidget.LARGE
    val narrow = size == StatusWidget.NARROW
    val showControls = size != StatusWidget.SMALL
    Column(
        modifier =
            GlanceModifier.fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surface)
                .padding(if (large) 10.dp else 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (large) {
            Header(state, strings)
            Spacer(GlanceModifier.height(6.dp))
        }
        when (state.status) {
            Status.NO_SERVER -> Message(strings.getString(CommonR.string.widget_no_server))
            Status.LOADING -> Message(strings.getString(CommonR.string.widget_loading))
            Status.CONNECTED,
            Status.OFFLINE -> {
                SpeedTiles(state, strings, labelled = showControls && !narrow, stacked = narrow)
                if (large && state.counts != null) {
                    Spacer(GlanceModifier.height(8.dp))
                    Counts(state.counts, strings)
                }
            }
        }
        if (showControls && state.status != Status.NO_SERVER) {
            // The stacked tiles stretch themselves; otherwise the controls sit at the bottom.
            Spacer(if (narrow) GlanceModifier.height(8.dp) else GlanceModifier.defaultWeight())
            Controls(
                state,
                strings,
                gap = if (narrow) 4.dp else 8.dp,
                height = if (large) 36.dp else 40.dp,
            )
        }
    }
}

@Composable
private fun Header(state: StatusWidgetState, strings: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.serverName ?: strings.getString(CommonR.string.app_name),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (state.status == Status.OFFLINE) {
            Text(
                strings.getString(CommonR.string.status_server_offline),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        modifier = GlanceModifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ColumnScope.SpeedTiles(
    state: StatusWidgetState,
    strings: Context,
    labelled: Boolean,
    stacked: Boolean,
) {
    val colors = GlanceTheme.colors
    val offline = state.status == Status.OFFLINE
    val download =
        @Composable { modifier: GlanceModifier ->
            SpeedTile(
                icon = R.drawable.ic_widget_download,
                label = strings.getString(CommonR.string.dl_speed),
                labelled = labelled,
                inline = stacked,
                value = state.dlSpeed,
                background = if (offline) colors.surfaceVariant else colors.primaryContainer,
                content = if (offline) colors.onSurfaceVariant else colors.onPrimaryContainer,
                strings = strings,
                modifier = modifier,
            )
        }
    val upload =
        @Composable { modifier: GlanceModifier ->
            SpeedTile(
                icon = R.drawable.ic_widget_upload,
                label = strings.getString(CommonR.string.up_speed),
                labelled = labelled,
                inline = stacked,
                value = state.upSpeed,
                background = if (offline) colors.surfaceVariant else colors.tertiaryContainer,
                content = if (offline) colors.onSurfaceVariant else colors.onTertiaryContainer,
                strings = strings,
                modifier = modifier,
            )
        }
    if (stacked) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            download(GlanceModifier.fillMaxWidth().defaultWeight())
            Spacer(GlanceModifier.height(6.dp))
            upload(GlanceModifier.fillMaxWidth().defaultWeight())
        }
    } else {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            download(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            upload(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun SpeedTile(
    icon: Int,
    label: String,
    labelled: Boolean,
    inline: Boolean,
    value: Long,
    background: ColorProvider,
    content: ColorProvider,
    strings: Context,
    modifier: GlanceModifier,
) {
    val arrow =
        @Composable {
            Image(
                ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size(16.dp),
            )
        }
    Column(
        modifier =
            modifier
                .cornerRadius(20.dp)
                .background(background)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inline) {
            // A full-width tile has room for the arrow beside the value, saving a line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                arrow()
                Spacer(GlanceModifier.width(6.dp))
                Rate(value, content, strings)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                arrow()
                if (labelled) {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(label, style = TextStyle(color = content, fontSize = 12.sp), maxLines = 1)
                }
            }
            Rate(value, content, strings)
        }
    }
}

@Composable
private fun Rate(value: Long, content: ColorProvider, strings: Context) {
    // A smaller unit keeps the number itself large without truncating in a 3-cell widget.
    val (number, unit) = splitRate(strings, value)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            number,
            style = TextStyle(color = content, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (unit.isNotEmpty()) {
            Spacer(GlanceModifier.width(3.dp))
            Text(
                unit,
                style =
                    TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 3.dp),
            )
        }
    }
}

private val BIDI_MARKS = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")

private fun splitRate(strings: Context, bytesPerSecond: Long): Pair<String, String> {
    val size = bytesPerSecond.toHumanReadable().replace(BIDI_MARKS, "").trim()
    val split = size.lastIndexOf(' ')
    if (split < 0) return strings.getString(CommonR.string.transfer_rate, size) to ""
    return size.substring(0, split) to
        strings.getString(CommonR.string.transfer_rate, size.substring(split + 1))
}

@Composable
private fun Counts(counts: StatusWidgetState.Counts, strings: Context) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Count(counts.downloading, strings.getString(CommonR.string.filter_downloading))
        Count(counts.seeding, strings.getString(CommonR.string.seeding))
        Count(counts.stopped, strings.getString(CommonR.string.stopped))
    }
}

@Composable
private fun RowScope.Count(value: Int, label: String) {
    // One line: a 3x2 on One UI is only ~174dp tall.
    Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Bottom) {
        Text(
            value.toString(),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Controls(state: StatusWidgetState, strings: Context, gap: Dp, height: Dp) {
    val colors = GlanceTheme.colors
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        ControlPill(
            R.drawable.ic_widget_pause,
            strings.getString(CommonR.string.widget_pause_all),
            widgetAction(StatusWidgetWorker.Action.PAUSE_ALL),
            height = height,
        )
        Spacer(GlanceModifier.width(gap))
        ControlPill(
            R.drawable.ic_widget_play,
            strings.getString(CommonR.string.widget_resume_all),
            widgetAction(StatusWidgetWorker.Action.RESUME_ALL),
            height = height,
        )
        Spacer(GlanceModifier.width(gap))
        // Filled while alternate limits are on, so the pill doubles as the mode indicator.
        ControlPill(
            R.drawable.ic_widget_speed,
            strings.getString(CommonR.string.use_alternate_speed_limits_label),
            widgetAction(StatusWidgetWorker.Action.TOGGLE_ALT_SPEED),
            background = if (state.altSpeedLimits) colors.primary else colors.surfaceVariant,
            content = if (state.altSpeedLimits) colors.onPrimary else colors.onSurfaceVariant,
            height = height,
        )
        Spacer(GlanceModifier.width(gap))
        ControlPill(
            R.drawable.ic_widget_refresh,
            strings.getString(CommonR.string.refresh_action),
            widgetAction(null),
            busy = state.busy,
            height = height,
        )
    }
}

@Composable
private fun RowScope.ControlPill(
    icon: Int,
    description: String,
    onClick: Action,
    background: ColorProvider = GlanceTheme.colors.surfaceVariant,
    content: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    busy: Boolean = false,
    height: Dp,
) {
    Box(
        modifier =
            GlanceModifier.defaultWeight()
                .height(height)
                .cornerRadius(20.dp)
                .background(background)
                .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = GlanceModifier.size(18.dp), color = content)
        } else {
            Image(
                ImageProvider(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}

private val actionKey = ActionParameters.Key<String>("action")

private fun widgetAction(action: StatusWidgetWorker.Action?) =
    actionRunCallback<StatusWidgetActionCallback>(
        actionParametersOf(actionKey to (action?.name ?: "")),
    )

/** Shows the press right away, then hands the network work to [StatusWidgetWorker]. */
class StatusWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action =
            parameters[actionKey]
                ?.takeIf { it.isNotEmpty() }
                ?.let(StatusWidgetWorker.Action::valueOf)
        StatusWidgetUpdater.update(context) {
            it[StatusWidgetState.Keys.busy] = true
            if (action == StatusWidgetWorker.Action.TOGGLE_ALT_SPEED) {
                it[StatusWidgetState.Keys.altSpeedLimits] =
                    !(it[StatusWidgetState.Keys.altSpeedLimits] ?: false)
            }
        }
        StatusWidgetWorker.runNow(context, action)
    }
}

// Receivers are created by the system, so they need no-argument constructors.
open class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    protected open val previewLayout: DpSize = StatusWidget.LARGE

    override val glanceAppWidget: GlanceAppWidget by lazy { StatusWidget(previewLayout) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // An update clears the rendered picker previews; don't wait for the app to be opened.
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    StatusWidget.publishPreviews(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // A newly placed (or restored) widget gets real numbers right away, not in 15 minutes.
        StatusWidgetWorker.runNow(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StatusWidgetWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Called per picker entry, so the other entries may still have widgets placed.
        if (!StatusWidgetUpdater.hasWidgets(context)) StatusWidgetWorker.cancelPeriodic(context)
    }
}

/** The same widget, offered at 3x1 in the picker (launchers list one size per receiver). */
class StatusWidgetSpeedsReceiver : StatusWidgetReceiver() {
    override val previewLayout = StatusWidget.SMALL
}

/** The same widget, offered at 2x2 in the picker. */
class StatusWidgetSquareReceiver : StatusWidgetReceiver() {
    override val previewLayout = StatusWidget.NARROW
}
