package dev.yashgarg.qbit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * Resolves a [Context] whose resources honour the language chosen in the app, for the places that
 * hold a non-Activity context.
 *
 * Below API 33 `AppCompatDelegate.setApplicationLocales` is a backport that only rewraps *Activity*
 * contexts, so string lookups made through the Application context - toasts raised from a
 * ViewModel, notification text built in a worker - would otherwise render in the device language
 * rather than the chosen one. Compose reads resources through the Activity and needs none of this.
 */
@SuppressLint("StaticFieldLeak") // caches only application contexts; see of()
object LocalizedContext {
    private var cachedTags: String? = null
    private var cached: Context? = null

    /**
     * [base]'s application context localised to the app's chosen language, or [base] itself when no
     * language is set or a wrapper cannot be built. Never throws: callers are usually about to show
     * a message, and failing to localise it is not worth losing it over.
     */
    fun of(base: Context): Context =
        runCatching {
                val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                if (tags.isEmpty()) return base

                // Wrapping the application context rather than [base] keeps this cache incapable of
                // retaining an Activity, whatever a caller passes in. Only string lookups go
                // through here, for which the two are equivalent.
                val appContext = base.applicationContext ?: return base

                // Rebuilt only when the language changes. The lookups behind this run per torrent
                // row (see NumberFormat), so creating a configuration context each time would be
                // hot, while comparing the tags is not.
                synchronized(this) {
                    if (tags != cachedTags || cached == null) {
                        val config =
                            Configuration(appContext.resources.configuration).apply {
                                setLocales(LocaleList.forLanguageTags(tags))
                            }
                        cached = appContext.createConfigurationContext(config)
                        cachedTags = tags
                    }
                    cached ?: base
                }
            }
            .getOrDefault(base)
}
