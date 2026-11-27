package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerPickerDropdown(
    name: String?,
    servers: List<ServerConfig>,
    activeServerId: Int,
    onSelect: (Int) -> Unit,
    appNavigator: AppNavigator,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(CommonR.string.switch_server_action)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .focusable()
                    .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                name ?: stringResource(CommonR.string.servers_title),
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = false,
        ) {
            servers.forEach { server ->
                val active = server.configId == activeServerId
                DropdownMenuItem(
                    text = { Text(server.serverName) },
                    modifier = Modifier.semantics { selected = active },
                    leadingIcon = {
                        if (active) Icon(Icons.Filled.Check, contentDescription = null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        onSelect(server.configId)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(CommonR.string.manage_servers_action)) },
                leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                onClick = {
                    expanded = false
                    appNavigator.navigate(NavCommand.OpenServerList)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CommonR.string.add_server)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    appNavigator.navigate(NavCommand.OpenConfig())
                },
            )
        }
    }
}
