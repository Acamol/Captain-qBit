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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR

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
        title = { Text(stringResource(CommonR.string.crash_report_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(CommonR.string.crash_report_saved_locally),
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
                TextButton(onClick = onCopy) { Text(stringResource(CommonR.string.copy_action)) }
                TextButton(
                    onClick = {
                        onReportIssue()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(CommonR.string.report_on_github_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.not_now_action)) }
        },
    )
}
