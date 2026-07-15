package dev.yashgarg.qbit

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.notifications.AppNotificationManager
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

        // Apply the saved theme (Light / Dark / Follow system) before any activity is created.
        // Material You dynamic colors are applied Compose-side by QbitComposeTheme.
        val prefs = runBlocking { serverPrefsStore.data.first() }
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        AppNotificationManager.createNotificationChannel(applicationContext)
    }
}
