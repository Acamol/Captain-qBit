package dev.yashgarg.qbit.ui.torrent.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.rememberCopyToClipboard

internal data class DetailField(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val stacked: Boolean = false,
)

@Composable
internal fun DetailSection(title: String, icon: ImageVector, fields: List<DetailField>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier =
                Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        fields.forEachIndexed { index, field ->
            val copy = rememberCopyToClipboard()
            val copied = stringResource(CommonR.string.clipboard_copied)
            val copyValue = {
                copy(
                    field.label,
                    listOfNotNull(field.value, field.supporting).joinToString("\n"),
                    copied,
                )
            }
            SegmentedListItem(
                modifier = Modifier.fillMaxWidth(),
                shapes = ListItemDefaults.segmentedShapes(index, fields.size),
                onClick = copyValue,
                onLongClick = copyValue,
                onLongClickLabel = stringResource(CommonR.string.copy_action),
                supportingContent =
                    if (field.stacked || field.supporting != null) {
                        {
                            Text(
                                if (field.stacked) field.value else field.supporting.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else null,
                trailingContent =
                    if (!field.stacked) {
                        { Text(field.value, style = MaterialTheme.typography.titleSmall) }
                    } else null,
            ) {
                Text(field.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
