package dev.yashgarg.qbit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Returns a copy action that writes text to the clipboard (with a confirmation toast) and fires a
 * long-press haptic, so every copy affordance buzzes consistently. Wraps
 * [ClipboardUtil.copyToClipboard]; uses [LocalHapticFeedback], so it needs no VIBRATE permission.
 */
@Composable
fun rememberCopyToClipboard(): (label: String, text: String, message: String) -> Unit {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    return remember(context, haptics) {
        { label, text, message ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            ClipboardUtil.copyToClipboard(context, label, text, message)
        }
    }
}
