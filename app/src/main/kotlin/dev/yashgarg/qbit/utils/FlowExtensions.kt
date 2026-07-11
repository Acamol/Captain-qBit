package dev.yashgarg.qbit.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Collects this flow only while [fragment]'s view is at least STARTED. */
fun <T> Flow<T>.collectWithLifecycle(fragment: Fragment, action: suspend (T) -> Unit) {
    flowWithLifecycle(fragment.viewLifecycleOwner.lifecycle)
        .onEach(action)
        .launchIn(fragment.viewLifecycleOwner.lifecycleScope)
}
