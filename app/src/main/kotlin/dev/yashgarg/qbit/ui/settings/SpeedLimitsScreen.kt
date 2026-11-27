package dev.yashgarg.qbit.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.SingleChoiceDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.SpeedLimits
import dev.yashgarg.qbit.ui.server.SpeedLimitsDialog
import dev.yashgarg.qbit.utils.friendlyMessage
import java.time.DayOfWeek
import java.time.format.TextStyle
import qbittorrent.models.SchedulerDays

/**
 * Everything about the server's speed limits in one place: the global limits, the alternate ones,
 * whether the alternate limits are active right now, and the schedule that switches between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedLimitsScreen(appNavigator: AppNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.loadSpeedLimits() }
    }
    val speedLimitMode by viewModel.speedLimitMode.collectAsStateWithLifecycle()
    val globalLimits by viewModel.globalLimits.collectAsStateWithLifecycle()
    val altLimits by viewModel.altLimits.collectAsStateWithLifecycle()
    // A plain value rather than a delegate, so the null checks below smart-cast it.
    val schedule = viewModel.altSpeedSchedule.collectAsStateWithLifecycle().value

    var globalBaseline by remember { mutableStateOf<SpeedLimits?>(null) }
    var altBaseline by remember { mutableStateOf<SpeedLimits?>(null) }
    var showDaysDialog by remember { mutableStateOf(false) }
    var editingEdge by remember { mutableStateOf<ScheduleEdge?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.speed_limits_title)) },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(CommonR.string.content_description_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(CommonR.string.global_speed_limits_section_title))
            SpeedLimitsRow(
                title = stringResource(CommonR.string.global_speed_limits_label),
                subtitle = stringResource(CommonR.string.global_speed_limits_subtitle),
                result = globalLimits,
                onEdit = { globalBaseline = it },
                onRetry = { viewModel.retrySpeedLimits(alternate = false) },
            )
            HorizontalDivider()
            SectionHeader(stringResource(CommonR.string.alternate_speed_limits_section_title))
            SwitchRow(
                stringResource(CommonR.string.use_alternate_speed_limits_label),
                speedLimitMode != 0,
                subtitle = stringResource(CommonR.string.use_alternate_speed_limits_subtitle),
            ) {
                viewModel.toggleSpeedLimits()
            }
            SpeedLimitsRow(
                title = stringResource(CommonR.string.alternate_speed_limits_label),
                subtitle = stringResource(CommonR.string.alternate_speed_limits_subtitle),
                result = altLimits,
                onEdit = { altBaseline = it },
                onRetry = { viewModel.retrySpeedLimits(alternate = true) },
            )
            SwitchRow(
                stringResource(CommonR.string.alt_speed_schedule_label),
                schedule?.enabled == true,
                subtitle = stringResource(CommonR.string.alt_speed_schedule_subtitle),
                enabled = schedule != null,
            ) {
                schedule?.let { loaded -> viewModel.setAltSpeedSchedule(loaded.copy(enabled = it)) }
            }
            if (schedule?.enabled == true) {
                ClickableRow(
                    title = stringResource(CommonR.string.alt_speed_schedule_from_label),
                    subtitle = timeOfDayLabel(context, schedule.fromMinutesOfDay),
                    onClick = { editingEdge = ScheduleEdge.FROM },
                )
                val endTime = timeOfDayLabel(context, schedule.toMinutesOfDay)
                ClickableRow(
                    title = stringResource(CommonR.string.alt_speed_schedule_to_label),
                    // An end before the start means the window crosses midnight. Saying so on
                    // the value itself keeps it visible however the list is scrolled.
                    subtitle =
                        if (schedule.toMinutesOfDay < schedule.fromMinutesOfDay) {
                            stringResource(CommonR.string.alt_speed_schedule_next_day, endTime)
                        } else {
                            endTime
                        },
                    onClick = { editingEdge = ScheduleEdge.TO },
                )
                ClickableRow(
                    title = stringResource(CommonR.string.alt_speed_schedule_days_label),
                    subtitle = schedulerDaysLabel(schedule.days),
                    onClick = { showDaysDialog = true },
                )
            }
        }
    }

    globalBaseline?.let {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.global_speed_limits_label),
            baseline = it,
            onConfirm = viewModel::setGlobalLimits,
            onDismiss = { globalBaseline = null },
        )
    }
    altBaseline?.let {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.alternate_speed_limits_label),
            baseline = it,
            onConfirm = viewModel::setAltLimits,
            onDismiss = { altBaseline = null },
        )
    }
    val edge = editingEdge
    if (edge != null && schedule != null) {
        val isFrom = edge == ScheduleEdge.FROM
        TimeOfDayDialog(
            title =
                stringResource(
                    if (isFrom) CommonR.string.alt_speed_schedule_from_label
                    else CommonR.string.alt_speed_schedule_to_label,
                ),
            initialMinutesOfDay =
                if (isFrom) schedule.fromMinutesOfDay else schedule.toMinutesOfDay,
            onConfirm = { minutes ->
                viewModel.setAltSpeedSchedule(
                    if (isFrom) schedule.copy(fromMinutesOfDay = minutes)
                    else schedule.copy(toMinutesOfDay = minutes),
                )
                editingEdge = null
            },
            onDismiss = { editingEdge = null },
        )
    }
    if (showDaysDialog && schedule != null) {
        val options = SchedulerDays.entries
        SingleChoiceDialog(
            title = stringResource(CommonR.string.alt_speed_schedule_days_label),
            labels = options.map { schedulerDaysLabel(it) },
            selectedIndex = options.indexOf(schedule.days),
            onSelect = { index ->
                viewModel.setAltSpeedSchedule(schedule.copy(days = options[index]))
                showDaysDialog = false
            },
            onDismiss = { showDaysDialog = false },
        )
    }
}

@Composable
private fun SpeedLimitsRow(
    title: String,
    subtitle: String,
    result: Result<SpeedLimits, Throwable>?,
    onEdit: (SpeedLimits) -> Unit,
    onRetry: () -> Unit,
) {
    val resources = LocalResources.current
    val limits = result?.get()
    val error = result?.getError()
    ClickableRow(
        title = title,
        subtitle =
            when {
                limits != null -> subtitle
                error != null ->
                    error.friendlyMessage(
                        resources::getString,
                        stringResource(CommonR.string.status_sync_data_failure),
                    )
                else -> stringResource(CommonR.string.loading_ellipsis)
            },
        subtitleIsError = error != null,
        enabled = limits != null,
        onClick = { limits?.let(onEdit) },
    )
    if (error != null) {
        TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(CommonR.string.retry_action))
        }
    }
}

private enum class ScheduleEdge {
    FROM,
    TO,
}

/**
 * Individual weekdays come from [DayOfWeek], which is already localised for every language the
 * platform knows - only the three grouped options need strings of their own.
 */
@Composable
private fun schedulerDaysLabel(days: SchedulerDays): String {
    // Composition's own locale, so the day names follow an in-app language override and
    // recompose if it changes - java.util.Locale.getDefault() would do neither.
    val locale = LocalLocale.current.platformLocale
    fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, locale)
    return when (days) {
        SchedulerDays.EVERY_DAY -> stringResource(CommonR.string.schedule_days_every_day)
        SchedulerDays.WEEKDAY -> stringResource(CommonR.string.schedule_days_weekday)
        SchedulerDays.WEEKEND -> stringResource(CommonR.string.schedule_days_weekend)
        SchedulerDays.MONDAY -> dayName(DayOfWeek.MONDAY)
        SchedulerDays.TUESDAY -> dayName(DayOfWeek.TUESDAY)
        SchedulerDays.WEDNESDAY -> dayName(DayOfWeek.WEDNESDAY)
        SchedulerDays.THURSDAY -> dayName(DayOfWeek.THURSDAY)
        SchedulerDays.FRIDAY -> dayName(DayOfWeek.FRIDAY)
        SchedulerDays.SATURDAY -> dayName(DayOfWeek.SATURDAY)
        SchedulerDays.SUNDAY -> dayName(DayOfWeek.SUNDAY)
    }
}
