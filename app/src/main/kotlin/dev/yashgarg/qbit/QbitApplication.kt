package dev.yashgarg.qbit

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.yashgarg.qbit.data.models.ServerPreferences
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

    @Inject lateinit var serverPrefsStore: DataStore<ServerPreferences>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        CrashHandler.install(this)
        AppContextHolder.init(this)

        // Apply the saved theme (Light / Dark / Follow system) before any activity is created.
        // Material You dynamic colors are applied Compose-side by QbitComposeTheme.
        val prefs = runBlocking { serverPrefsStore.data.first() }
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        // Below API 33 setApplicationLocales holds the choice in memory only, so it has to be
        // restored here or the app comes up in the system language after every cold start. On 33+
        // the framework persists it itself and this just re-states the current value.
        if (prefs.languageTag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(prefs.languageTag)
            )
        }

        AppNotificationManager.createNotificationChannel(LocalizedContext.of(applicationContext))
    }
}
