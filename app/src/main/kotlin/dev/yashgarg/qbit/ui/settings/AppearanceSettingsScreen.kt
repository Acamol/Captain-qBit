package dev.yashgarg.qbit.ui.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.utils.LocalizedContext

private val THEME_OPTIONS =
    listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to CommonR.string.theme_system_default,
        AppCompatDelegate.MODE_NIGHT_NO to CommonR.string.theme_light,
        AppCompatDelegate.MODE_NIGHT_YES to CommonR.string.theme_dark,
    )

@Composable
private fun themeLabel(mode: Int): String =
    stringResource(
        THEME_OPTIONS.firstOrNull { it.first == mode }?.second
            ?: CommonR.string.theme_system_default,
    )

// Per-app language: "" is the system-default locale list. Add a language's tag + label here (and
// to res/xml/locale_config.xml) as translations land.
private val LANGUAGE_OPTIONS =
    listOf(
        "" to CommonR.string.theme_system_default,
        "en" to CommonR.string.language_english,
        "he" to CommonR.string.language_hebrew,
        "ru" to CommonR.string.language_russian,
    )

@Composable
private fun languageLabel(tag: String): String =
    stringResource(
        LANGUAGE_OPTIONS.firstOrNull { it.first == tag }?.second
            ?: CommonR.string.theme_system_default,
    )

@Composable
fun AppearanceSettingsScreen(
    appNavigator: AppNavigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColors by viewModel.dynamicColors.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    // Seeded from the live locale rather than the preference: on API 33+ the two can diverge
    // if the language was changed from the system's per-app language screen.
    var currentLanguageTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    SettingsSubScreen(stringResource(CommonR.string.appearance_section_title), appNavigator) {
        ClickableRow(
            title = stringResource(CommonR.string.theme_label),
            subtitle = themeLabel(themeMode),
            onClick = { showThemeDialog = true },
        )
        ClickableRow(
            title = stringResource(CommonR.string.language_label),
            subtitle = languageLabel(currentLanguageTag),
            onClick = { showLanguageDialog = true },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                stringResource(CommonR.string.dynamic_colors_label),
                dynamicColors,
                subtitle = stringResource(CommonR.string.dynamic_colors_subtitle),
            ) {
                viewModel.setDynamicColors(it)
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(CommonR.string.theme_label)) },
            text = {
                Column {
                    THEME_OPTIONS.forEach { (mode, labelRes) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }

    if (showLanguageDialog) {
        val selectLanguage: (String) -> Unit = { tag ->
            // Applied here rather than left to MainActivity's observer, so this works even when the
            // stored tag is stale - on API 33+ the system's own per-app language screen can change
            // the locale without this app knowing. The preference is what makes the choice survive
            // a cold start below API 33, where setApplicationLocales keeps it in memory only.
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(tag),
            )
            viewModel.setLanguageTag(tag)
            // Android caches channel names from creation time, so a new locale alone won't
            // retranslate them. Recreating with the same ids updates the names in place.
            AppNotificationManager.createNotificationChannel(LocalizedContext.of(context))
            currentLanguageTag = tag
            showLanguageDialog = false
        }
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(CommonR.string.language_label)) },
            text = {
                Column {
                    LANGUAGE_OPTIONS.forEach { (tag, labelRes) ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { selectLanguage(tag) }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentLanguageTag == tag,
                                onClick = { selectLanguage(tag) },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(CommonR.string.cancel))
                }
            },
        )
    }
}
