package dev.yashgarg.qbit

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
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

        // Material You: adopt the device's wallpaper-based palette on Android 12+, but only when
        // the user has opted in (Settings -> Dynamic colors). The precondition is checked per
        // activity on creation, so recreating the activity after the toggle changes applies it.
        val prefs = runBlocking { serverPrefsStore.data.first() }

        // Apply the saved theme (Light / Dark / Follow system) before any activity is created.
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        dynamicColorsEnabled = prefs.dynamicColors
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder().setPrecondition { _, _ -> dynamicColorsEnabled }.build(),
        )

        AppNotificationManager.createNotificationChannel(applicationContext)
    }

    companion object {
        /**
         * Cached mirror of the dynamic-colors preference, read by the [DynamicColors] precondition
         * (which must run synchronously). Updated from Settings before recreating the activity.
         */
        @Volatile var dynamicColorsEnabled = false
    }
}
