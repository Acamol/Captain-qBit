package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR

@Composable
internal fun SelectionActionsToolbar(
    onResume: () -> Unit,
    onPause: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors =
            FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.primary,
                toolbarContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            // Flush edges avoid extra height from the toolbar's touch-target padding balancing.
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SelectionAction(stringResource(CommonR.string.resume), Icons.Filled.PlayArrow, onResume)
            SelectionAction(
                stringResource(CommonR.string.pause_action),
                Icons.Filled.Pause,
                onPause,
            )
            SelectionAction(
                stringResource(CommonR.string.content_description_more),
                Icons.Filled.MoreVert,
                onMore,
            )
        }
    }
}

@Composable
private fun SelectionAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, textAlign = TextAlign.Center)
    }
}
