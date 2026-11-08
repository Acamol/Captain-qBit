package dev.yashgarg.qbit.ui.compose

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

/**
 * How many dialogs the screens below [LocalOpenDialogs] have open. Dialogs that open by themselves
 * rather than from the screen in use (What's New, a crash report, a backup file opened from another
 * app) wait while it isn't 0, so they never stack on top of one the user is working in.
 */
class OpenDialogs {
    var count by mutableIntStateOf(0)
        private set

    internal fun opened() {
        count++
    }

    internal fun closed() {
        count--
    }
}

/** Where the dialogs below count themselves as open. Outside it, they aren't counted. */
val LocalOpenDialogs = staticCompositionLocalOf<OpenDialogs?> { null }

@Composable
private fun CountAsOpen() {
    val openDialogs = LocalOpenDialogs.current ?: return
    DisposableEffect(openDialogs) {
        openDialogs.opened()
        onDispose { openDialogs.closed() }
    }
}

/** Material's [androidx.compose.material3.AlertDialog], counted in [LocalOpenDialogs]. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    CountAsOpen()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** Compose's [androidx.compose.ui.window.Dialog], counted in [LocalOpenDialogs]. */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    CountAsOpen()
    androidx.compose.ui.window.Dialog(onDismissRequest, properties, content)
}
