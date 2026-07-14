package dev.yashgarg.qbit.ui.whatsnew

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the per-release "What's New" notes bundled into the APK by the `syncChangelogAssets` Gradle
 * task. The source of truth is the fastlane changelog
 * (`fastlane/.../changelogs/<versionCode>.txt`), so what F-Droid shows on its listing and what the
 * app shows in-app stay identical.
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
