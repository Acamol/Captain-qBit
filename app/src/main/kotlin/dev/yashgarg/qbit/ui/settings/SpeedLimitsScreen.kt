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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.SingleChoiceDialog
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.SpeedLimitsDialog
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
    val speedLimitMode by viewModel.speedLimitMode.collectAsStateWithLifecycle()
    val globalDownloadLimit by viewModel.globalDownloadLimit.collectAsStateWithLifecycle()
    val globalUploadLimit by viewModel.globalUploadLimit.collectAsStateWithLifecycle()
    val altDownloadLimit by viewModel.altDownloadLimit.collectAsStateWithLifecycle()
    val altUploadLimit by viewModel.altUploadLimit.collectAsStateWithLifecycle()
    // A plain value rather than a delegate, so the null checks below smart-cast it.
    val schedule = viewModel.altSpeedSchedule.collectAsStateWithLifecycle().value

    var showGlobalLimitsDialog by remember { mutableStateOf(false) }
    var showAltLimitsDialog by remember { mutableStateOf(false) }
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(CommonR.string.global_speed_limits_section_title))
            ClickableRow(
                title = stringResource(CommonR.string.global_speed_limits_label),
                subtitle = stringResource(CommonR.string.global_speed_limits_subtitle),
                onClick = { showGlobalLimitsDialog = true },
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
            ClickableRow(
                title = stringResource(CommonR.string.alternate_speed_limits_label),
                subtitle = stringResource(CommonR.string.alternate_speed_limits_subtitle),
                onClick = { showAltLimitsDialog = true },
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

    if (showGlobalLimitsDialog) {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.global_speed_limits_label),
            initialDownloadBytes = globalDownloadLimit,
            initialUploadBytes = globalUploadLimit,
            onConfirm = { dl, ul -> viewModel.setGlobalLimits(dl, ul) },
            onDismiss = { showGlobalLimitsDialog = false },
        )
    }
    if (showAltLimitsDialog) {
        SpeedLimitsDialog(
            title = stringResource(CommonR.string.alternate_speed_limits_label),
            initialDownloadBytes = altDownloadLimit,
            initialUploadBytes = altUploadLimit,
            onConfirm = { dl, ul -> viewModel.setAltLimits(dl, ul) },
            onDismiss = { showAltLimitsDialog = false },
        )
    }
    val edge = editingEdge
    if (edge != null && schedule != null) {
        val isFrom = edge == ScheduleEdge.FROM
        TimeOfDayDialog(
            title =
                stringResource(
                    if (isFrom) CommonR.string.alt_speed_schedule_from_label
                    else CommonR.string.alt_speed_schedule_to_label
                ),
            initialMinutesOfDay =
                if (isFrom) schedule.fromMinutesOfDay else schedule.toMinutesOfDay,
            onConfirm = { minutes ->
                viewModel.setAltSpeedSchedule(
                    if (isFrom) schedule.copy(fromMinutesOfDay = minutes)
                    else schedule.copy(toMinutesOfDay = minutes)
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
