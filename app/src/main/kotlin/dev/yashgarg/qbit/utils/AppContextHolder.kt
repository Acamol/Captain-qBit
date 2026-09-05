package dev.yashgarg.qbit.utils

import android.annotation.SuppressLint
import android.content.Context

/** Holds the application [Context] for utilities that need string resources but aren't DI-aware. */
@SuppressLint("StaticFieldLeak") // always the Application context, never an Activity
object AppContextHolder {
    lateinit var context: Context

    /**
     * [context] with the app's chosen language applied - what string lookups should use, since the
     * Application context alone ignores that choice below API 33. See [LocalizedContext].
     */
    val localized: Context
        get() = LocalizedContext.of(context)

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
