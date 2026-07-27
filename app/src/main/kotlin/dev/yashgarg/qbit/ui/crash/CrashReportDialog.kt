package dev.yashgarg.qbit.ui.crash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
 * Shown once after a crash, from a report saved locally by [dev.yashgarg.qbit.utils.CrashHandler].
 * Nothing here is sent automatically — the user chooses whether to copy it or attach it to a GitHub
 * issue.
 */
@Composable
fun CrashReportDialog(
    report: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReportIssue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Captain qBit crashed last time") },
        text = {
            Column {
                Text(
                    "Saved on this device only — nothing is sent automatically.",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = report,
                    modifier =
                        Modifier.padding(top = 8.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(
                    onClick = {
                        onReportIssue()
                        onDismiss()
                    }
                ) {
                    Text("Report on GitHub")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
