package dev.yashgarg.qbit.ui.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.FullScreenEditor

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
    FullScreenEditor(
        onDismiss = onDismiss,
        title = stringResource(CommonR.string.crash_report_dialog_title),
        bottomBar = {
            Surface {
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CommonR.string.not_now_action))
                    }
                    TextButton(onClick = onCopy) {
                        Text(stringResource(CommonR.string.copy_action))
                    }
                    FilledTonalButton(
                        onClick = {
                            onReportIssue()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(CommonR.string.report_on_github_action))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                stringResource(CommonR.string.crash_report_saved_locally),
                fontWeight = FontWeight.Medium,
            )
            SelectionContainer {
                Text(
                    text = report,
                    modifier = Modifier.padding(top = 16.dp),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = TextDirection.Ltr,
                        ),
                )
            }
        }
    }
}
