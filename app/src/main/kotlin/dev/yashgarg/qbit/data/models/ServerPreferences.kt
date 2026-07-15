package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** Per-server filter + sort selections, so each server restores its own view. */
@Keep
@Serializable
data class ServerViewPrefs(
    val sortOptionName: String = "NAME",
    val sortDirectionAsc: Boolean = true,
    val filterStateName: String = "ALL",
    val filterCategory: String? = null,
    val filterTracker: String? = null,
    val filterTags: Set<String> = emptySet(),
    val filterUntagged: Boolean = false,
)

@Keep
@Serializable
data class ServerPreferences(
    val addTorrentAutoTmm: Boolean = false,
    val addTorrentPaused: Boolean = false,
    val addTorrentCategory: String = "",
    // Legacy global filter/sort (pre-per-server). Kept for backward-compatible deserialization;
    // superseded by [serverViewPrefs]. See ServerViewModel.
    val sortOptionName: String = "NAME",
    val sortDirectionAsc: Boolean = true,
    val filterStateName: String = "ALL",
    val filterCategory: String? = null,
    val filterTracker: String? = null,
    val filterTags: Set<String> = emptySet(),
    val filterUntagged: Boolean = false,
    /** Per-server view state, keyed by [ServerConfig.configId]. */
    val serverViewPrefs: Map<Int, ServerViewPrefs> = emptyMap(),
    val dynamicColors: Boolean = false,
    val activeServerId: Int = -1,
    val categoryColors: Map<String, Int> = emptyMap(),
    val statusNotification: Boolean = true,
    val notifyOnComplete: Boolean = false,
    val notifyOnChecked: Boolean = false,
    // AppCompatDelegate night-mode constant. Defaults to MODE_NIGHT_YES (2) to preserve the
    // app's original dark-only behaviour for existing installs.
    val themeMode: Int = 2,
    // Highest versionCode whose "What's New" has been shown. 0 = never recorded (fresh install),
    // so the dialog is skipped on first run and only appears after an upgrade.
    val lastSeenVersionCode: Int = 0,
)
