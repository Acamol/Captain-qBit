package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR

@Composable
fun SingleChoiceSheet(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    newLabel: String? = null,
    onNew: (() -> Unit)? = null,
    dismissLabel: String? = null,
    header: @Composable (() -> Unit)? = null,
) {
    ChoiceSheet(
        title = title,
        labels = labels,
        selected = setOf(selectedIndex),
        multiple = false,
        onToggle = onSelect,
        onDismiss = onDismiss,
        newLabel = newLabel,
        onNew = onNew,
        dismissLabel = dismissLabel,
        header = header,
    )
}

@Composable
fun MultipleChoiceSheet(
    title: String,
    labels: List<String>,
    initialSelected: Set<Int>,
    onApply: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    newLabel: String? = null,
    onNew: (() -> Unit)? = null,
) {
    var selected by remember(labels, initialSelected) { mutableStateOf(initialSelected) }
    ChoiceSheet(
        title = title,
        labels = labels,
        selected = selected,
        multiple = true,
        onToggle = { index ->
            selected = if (index in selected) selected - index else selected + index
        },
        onDismiss = onDismiss,
        onApply = { onApply(selected) },
        newLabel = newLabel,
        onNew = onNew,
    )
}

@Composable
private fun ChoiceSheet(
    title: String,
    labels: List<String>,
    selected: Set<Int>,
    multiple: Boolean,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onApply: (() -> Unit)? = null,
    newLabel: String? = null,
    onNew: (() -> Unit)? = null,
    dismissLabel: String? = null,
    header: @Composable (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val visible = labels.indices.filter { labels[it].contains(query, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            header?.invoke()
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(CommonR.string.content_description_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            LazyColumn(
                Modifier.weight(1f, fill = false)
                    .then(if (multiple) Modifier else Modifier.selectableGroup()),
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(CommonR.string.no_content_found),
                            Modifier.padding(24.dp),
                        )
                    }
                }
                items(visible, key = { it }) { index ->
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        if (multiple) {
                            CheckboxRow(labels[index], index in selected) { onToggle(index) }
                        } else {
                            SelectableRow(labels[index], index in selected) { onToggle(index) }
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onNew != null && newLabel != null) {
                    TextButton(onClick = onNew) { Text(newLabel) }
                }
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel ?: stringResource(CommonR.string.cancel))
                }
                if (onApply != null) {
                    TextButton(onClick = onApply) { Text(stringResource(CommonR.string.apply)) }
                }
            }
        }
    }
}
