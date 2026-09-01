package dev.yashgarg.qbit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources

/**
 * A resolver for [Throwable.friendlyMessage], for call sites that need it inside a non-composable
 * callback (a Toast/Snackbar triggered by a one-shot event) rather than directly in composition.
 * Resources are read from [LocalResources] so a configuration change (a locale switch, say)
 * re-composes callers rather than leaving them holding stale text, and every string id resolves -
 * including ones this file has never heard of, such as the `unknown_error` default that
 * [Throwable.friendlyMessage] falls back to.
 */
@Composable
fun rememberFriendlyMessageResolver(): (Int) -> String {
    val resources = LocalResources.current
    return remember(resources) { { id -> resources.getString(id) } }
}
