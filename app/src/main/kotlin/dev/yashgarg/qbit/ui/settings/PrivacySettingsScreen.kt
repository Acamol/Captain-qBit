package dev.yashgarg.qbit.ui.settings

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.manager.AppLockSettings
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import java.util.Locale

private val LOCK_TIMEOUT_OPTIONS =
    listOf(
        0L to CommonR.string.lock_timeout_immediately,
        30_000L to CommonR.string.interval_30_seconds,
        60_000L to CommonR.string.interval_1_minute,
        300_000L to CommonR.string.interval_5_minutes,
        900_000L to CommonR.string.lock_timeout_15_minutes,
        3_600_000L to CommonR.string.lock_timeout_1_hour,
        AppLockSettings.TIMEOUT_NEVER to CommonR.string.lock_timeout_never,
    )

@Composable
private fun lockTimeoutLabel(ms: Long): String {
    val option = LOCK_TIMEOUT_OPTIONS.firstOrNull { it.first == ms }
    if (option != null) return stringResource(option.second)
    // Only a backup can set a timeout that isn't an option: shown as it is, not as the nearest one.
    return formatDuration(ms, LocalConfiguration.current.locales[0])
}

private fun formatDuration(ms: Long, locale: Locale): String {
    val (amount, unit) =
        when {
            ms % 3_600_000L == 0L -> ms / 3_600_000L to MeasureUnit.HOUR
            ms % 60_000L == 0L -> ms / 60_000L to MeasureUnit.MINUTE
            else -> (ms + 999L) / 1_000L to MeasureUnit.SECOND
        }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
        .format(Measure(amount, unit))
}

@Composable
fun PrivacySettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val appLockEnabled = prefs.appLockEnabled
    val appLockTimeoutMs = prefs.appLockTimeoutMs
    val appLockOnScreenOff = prefs.appLockOnScreenOff
    val hideInRecents = prefs.hideInRecents
    val blockScreenshots = prefs.blockScreenshots
    val appLockConfirmTitle = stringResource(CommonR.string.app_lock_confirm_title)
    // Re-checked on resume, so setting up a screen lock in the system settings and coming back
    // enables the switch straight away.
    var appLockAvailable by remember { mutableStateOf(viewModel.isAppLockAvailable()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appLockAvailable = viewModel.isAppLockAvailable()
    }
    var showLockTimeoutDialog by remember { mutableStateOf(false) }

    SettingsSubScreen(stringResource(CommonR.string.privacy_section_title), appNavigator) {
        SwitchRow(
            stringResource(CommonR.string.app_lock_label),
            appLockEnabled,
            subtitle =
                stringResource(
                    if (!appLockAvailable && !appLockEnabled) {
                        CommonR.string.app_lock_unavailable_subtitle
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        // The lock blanks the recents preview, which takes FLAG_SECURE here.
                        CommonR.string.app_lock_subtitle_blocks_screenshots
                    } else {
                        CommonR.string.app_lock_subtitle
                    },
                ),
            // Kept usable while on, so it can always be turned off again.
            enabled = appLockAvailable || appLockEnabled,
        ) { enabled ->
            viewModel.setAppLockEnabled(appLockConfirmTitle, enabled)
        }
        if (appLockEnabled) {
            ClickableRow(
                title = stringResource(CommonR.string.app_lock_timeout_label),
                subtitle = lockTimeoutLabel(appLockTimeoutMs),
                onClick = { showLockTimeoutDialog = true },
            )
            SwitchRow(
                stringResource(CommonR.string.lock_on_screen_off_label),
                appLockOnScreenOff,
                subtitle = stringResource(CommonR.string.lock_on_screen_off_subtitle),
            ) {
                viewModel.setAppLockOnScreenOff(appLockConfirmTitle, it)
            }
        }
        HorizontalDivider()
        SwitchRow(
            stringResource(CommonR.string.hide_in_recents_label),
            hideInRecents,
            subtitle =
                stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        CommonR.string.hide_in_recents_subtitle
                    } else {
                        CommonR.string.hide_in_recents_subtitle_blocks_screenshots
                    },
                ),
        ) {
            viewModel.setHideInRecents(it)
        }
        SwitchRow(
            stringResource(CommonR.string.block_screenshots_label),
            blockScreenshots,
            subtitle = stringResource(CommonR.string.block_screenshots_subtitle),
        ) {
            viewModel.setBlockScreenshots(it)
        }
    }

    if (showLockTimeoutDialog) {
        IntervalDialog(
            title = stringResource(CommonR.string.app_lock_timeout_label),
            description = stringResource(CommonR.string.app_lock_timeout_description),
            selected = appLockTimeoutMs,
            onSelect = { viewModel.setAppLockTimeoutMs(appLockConfirmTitle, it) },
            onDismiss = { showLockTimeoutDialog = false },
            options = LOCK_TIMEOUT_OPTIONS,
        )
    }
}
