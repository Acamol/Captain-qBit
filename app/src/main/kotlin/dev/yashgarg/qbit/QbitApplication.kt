package dev.yashgarg.qbit

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.utils.AppContextHolder
import dev.yashgarg.qbit.utils.CrashHandler
import dev.yashgarg.qbit.utils.LocalizedContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class QbitApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var appPrefsStore: DataStore<AppPreferences>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        CrashHandler.install(this)
        AppContextHolder.init(this)

        // Apply the saved theme (Light / Dark / Follow system) before any activity is created.
        // Material You dynamic colors are applied Compose-side by QbitComposeTheme.
        val prefs = runBlocking { appPrefsStore.data.first() }
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        // Below API 33 setApplicationLocales holds the choice in memory only, so it has to be
        // restored here or the app comes up in the system language after every cold start. From 33
        // the framework persists it, and it is also where the user may have set the language from
        // Android's own per-app language screen - which this preference would not know about, so
        // don't re-assert it there.
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && prefs.languageTag.isNotEmpty()
        ) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(prefs.languageTag)
            )
        }

        AppNotificationManager.createNotificationChannel(LocalizedContext.of(applicationContext))
    }
}
