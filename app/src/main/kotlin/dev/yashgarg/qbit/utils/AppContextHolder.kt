package dev.yashgarg.qbit.utils

import android.annotation.SuppressLint
import android.content.Context

/** Holds the application [Context] for utilities that need string resources but aren't DI-aware. */
@SuppressLint("StaticFieldLeak") // always the Application context, never an Activity
object AppContextHolder {
    lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
