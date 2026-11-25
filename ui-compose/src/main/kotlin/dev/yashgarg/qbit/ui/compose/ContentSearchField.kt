package dev.yashgarg.qbit.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR

/** File-scoped search, either a temporary focused field or a persistent filter in the Files tab. */
@Composable
fun ContentSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    persistent: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    val colors =
        TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        )

    TextField(
        value = query,
        onValueChange = onQueryChange,
        label =
            if (persistent) {
                { Text(stringResource(CommonR.string.search_files_label)) }
            } else null,
        placeholder =
            if (!persistent) {
                { Text(stringResource(CommonR.string.search_files_placeholder)) }
            } else null,
        singleLine = true,
        shape = if (persistent) MaterialTheme.shapes.extraLarge else TextFieldDefaults.shape,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon =
            if (!persistent || query.isNotEmpty()) {
                {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription =
                                stringResource(
                                    if (persistent) CommonR.string.content_description_clear
                                    else CommonR.string.content_description_close_search,
                                ),
                        )
                    }
                }
            } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors =
            if (persistent) {
                colors.copy(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            } else colors,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = if (persistent) 16.dp else 12.dp, vertical = 4.dp)
                .focusRequester(focus),
    )
}
