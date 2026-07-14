package dev.yashgarg.qbit.ui.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A simple "What's New" dialog listing the current release's highlights as bullets. Shown once
 * after an upgrade (driven by [WhatsNewViewModel]) and on demand from the About screen.
 */
@Composable
fun WhatsNewDialog(versionName: String, entries: List<String>, onDismiss: () -> Unit) {
    if (entries.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New in v$versionName") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("•  ", fontWeight = FontWeight.Bold)
                        Text(entry)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
