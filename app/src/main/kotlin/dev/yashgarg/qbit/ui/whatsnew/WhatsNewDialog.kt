package dev.yashgarg.qbit.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.FullScreenEditor

/**
 * A full-screen reader listing the current release's highlights. Shown once after an upgrade
 * (driven by [WhatsNewViewModel]) and on demand from the About screen.
 */
@Composable
fun WhatsNewDialog(versionName: String, entries: List<String>, onDismiss: () -> Unit) {
    if (entries.isEmpty()) return

    FullScreenEditor(
        onDismiss = onDismiss,
        title = stringResource(CommonR.string.whats_new_in_version_title, versionName),
        bottomBar = {
            Surface {
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(CommonR.string.got_it_action))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            entries.forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•  ", fontWeight = FontWeight.Bold)
                    Text(entry)
                }
            }
        }
    }
}
