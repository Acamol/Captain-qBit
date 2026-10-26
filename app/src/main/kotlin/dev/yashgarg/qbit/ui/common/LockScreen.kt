package dev.yashgarg.qbit.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.yashgarg.qbit.common.R as CommonR

/** The lock screen itself: shown by [dev.yashgarg.qbit.LockActivity], which covers the app. */
@Composable
fun LockScreenContent(onUnlock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(24.dp))
            Text(
                stringResource(CommonR.string.app_locked_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(32.dp))
            Button(onClick = onUnlock) { Text(stringResource(CommonR.string.unlock_action)) }
        }
    }
}

/**
 * The same screen as a dialog over the main activity's content, for the moment between the app
 * being shown while locked and [dev.yashgarg.qbit.LockActivity] covering it. A dialog rather than a
 * layer over the content, because anything the app already had open in a dialog of its own would
 * sit above an in-window layer.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, onBack: () -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        LockScreenContent(onUnlock)
    }
}
