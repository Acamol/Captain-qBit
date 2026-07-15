package dev.yashgarg.qbit.ui.whatsnew

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the per-release "What's New" notes bundled into the APK by the `syncChangelogAssets` Gradle
 * task. That task overlays the full in-app notes (`whatsnew/<versionCode>.txt`, no length limit) on
 * top of the short fastlane summary (`fastlane/.../changelogs/<versionCode>.txt`, kept ≤500 chars
 * because F-Droid truncates its listing there). So in-app this shows the full notes when a
 * `whatsnew/` file exists, otherwise it falls back to the same short summary F-Droid shows.
 */
object ChangelogAssets {

    /**
     * Returns the changelog bullet lines for [versionCode], or an empty list if none is bundled
     * (e.g. a release that shipped without notes). Never throws.
     */
    suspend fun read(context: Context, versionCode: Int): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                    context.assets.open("changelogs/$versionCode.txt").bufferedReader().use { reader
                        ->
                        reader
                            .readText()
                            .lineSequence()
                            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                            .filter { it.isNotEmpty() }
                            .toList()
                    }
                }
                .getOrDefault(emptyList())
        }
}
