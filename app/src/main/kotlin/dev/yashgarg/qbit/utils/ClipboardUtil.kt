package dev.yashgarg.qbit.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService

object ClipboardUtil {
    /**
     * The clipboard's first item as text, or an empty string when there's nothing readable. Uses
     * coerceToText (handles text/plain, styled text, and text/html) rather than requiring a strict
     * text/plain MIME and reading .text directly, which returned empty for many real clipboard
     * contents.
     */
    fun getClipboardText(context: Context): String {
        val clipboard = getSystemService(context, ClipboardManager::class.java) ?: return ""
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    fun copyToClipboard(
        context: Context,
        label: String,
        text: String,
        message: String = "Copied to clipboard"
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
