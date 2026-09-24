package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR

/**
 * The dialog shapes shared across the app's screens. Each takes its labels as strings so callers
 * keep ownership of their own wording, and each supplies the Cancel button itself so the dismiss
 * action reads the same everywhere.
 */

/**
 * A single text field with validation. [validate] returns an error message to show beneath the
 * field, or null to accept the (trimmed) value and call [onConfirm]. [extraContent] adds further
 * controls below the field for dialogs that need more than one input.
 *
 * [label] and [supportingText] are optional: a field whose dialog title already says what it holds
 * needs neither. An error from [validate] replaces [supportingText] while it is showing.
 */
@Composable
fun TextInputDialog(
    title: String,
    confirmLabel: String,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String? = null,
    supportingText: String? = null,
    initial: String = "",
    singleLine: Boolean = true,
    extraContent: @Composable (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val hint = error ?: supportingText
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = label?.let { { Text(it) } },
                    isError = error != null,
                    supportingText = hint?.let { { Text(it) } },
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                extraContent?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val err = validate(value.trim())
                    if (err != null) error = err else onConfirm(value)
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** A title and a single confirming action, for "are you sure" prompts. */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** [ConfirmDialog] with a second, non-destructive action beside Cancel. */
@Composable
fun ThreeActionDialog(
    title: String,
    positiveLabel: String,
    onPositive: () -> Unit,
    neutralLabel: String,
    onNeutral: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onPositive) { Text(positiveLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNeutral) { Text(neutralLabel) }
                TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
            }
        },
    )
}

/** A radio list. Selecting a row reports it immediately; the only button dismisses. */
@Composable
fun SingleChoiceDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { i, label ->
                    SelectableRow(
                        label = label,
                        selected = i == selectedIndex,
                        onClick = { onSelect(i) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.cancel)) }
        },
    )
}

/** A radio button and label, as used by [SingleChoiceDialog]. */
@Composable
fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

/** A checkbox and label, for dialogs offering an extra option alongside their main action. */
@Composable
fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
