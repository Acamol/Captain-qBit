package dev.yashgarg.qbit.ui.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.ContentSearchField
import dev.yashgarg.qbit.ui.compose.Dialog
import dev.yashgarg.qbit.ui.compose.FormSection
import dev.yashgarg.qbit.ui.compose.FormSwitch
import dev.yashgarg.qbit.ui.compose.TorrentContentSelectionView
import dev.yashgarg.qbit.ui.compose.allFileIndices
import dev.yashgarg.qbit.ui.server.FileSelectionUiState
import dev.yashgarg.qbit.ui.server.ServerViewModel
import dev.yashgarg.qbit.ui.torrent.filterContentTree
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.validation.LinkValidator

private const val TORRENT_MIMETYPE = "application/x-bittorrent"

private val IntSetSaver =
    Saver<Set<Int>, IntArray>(save = { it.toIntArray() }, restore = { it.toSet() })

/**
 * Full-screen add-torrent screen with two tabs: Options (link/file, category, save path, switches)
 * and Files (the torrent's content tree with checkboxes). The Files tab fills in instantly for a
 * `.torrent` (parsed locally) and shows a loading state for magnets while their metadata is fetched
 * via a hidden stopped add — cancelled again if the screen is dismissed without adding.
 *
 * Native Compose port of the former `AddTorrentDialog` DialogFragment. Shares the caller's
 * [ServerViewModel] (add/prepare/confirm act on the shared client singletons, so the main list
 * picks up the result on its next sync).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTorrentScreen(
    viewModel: ServerViewModel,
    availableCategories: List<String>,
    defaultAutoTmm: Boolean,
    defaultPaused: Boolean,
    defaultCategory: String,
    prefillUrl: String?,
    prefillFileUri: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val linkValidator = remember { LinkValidator() }
    val noFileSelectedMessage = stringResource(CommonR.string.no_file_selected)
    val clipboardEmptyMessage = stringResource(CommonR.string.clipboard_empty)
    val confirming by viewModel.confirmingAdd.collectAsState()
    val addConfirmed by viewModel.addConfirmed.collectAsState()
    val fileSelection by viewModel.fileSelection.collectAsState()
    val confirmationError = fileSelection?.confirmationError

    LaunchedEffect(addConfirmed) {
        if (addConfirmed) {
            viewModel.consumeAddConfirmation()
            onDismiss()
        }
    }

    var magnetText by rememberSaveable { mutableStateOf(prefillUrl.orEmpty()) }
    var pickedUri by rememberSaveable { mutableStateOf(prefillFileUri) }
    var preparedMagnet by rememberSaveable { mutableStateOf<String?>(null) }
    var deselected by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet()) }
    var filesUnavailableReason by rememberSaveable { mutableStateOf<Int?>(null) }
    var magnetError by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    var filesSearchOpen by rememberSaveable { mutableStateOf(false) }
    var filesQuery by rememberSaveable { mutableStateOf("") }

    var category by rememberSaveable { mutableStateOf(defaultCategory) }
    var saveCategory by rememberSaveable { mutableStateOf(defaultCategory.isNotBlank()) }
    var autoTmm by rememberSaveable { mutableStateOf(defaultAutoTmm) }
    var paused by rememberSaveable { mutableStateOf(defaultPaused) }
    var savePath by rememberSaveable { mutableStateOf("") }

    // Surface the saved default category first so it's the top suggestion.
    val orderedCategories =
        remember(availableCategories, defaultCategory) {
            if (defaultCategory.isNotBlank() && availableCategories.contains(defaultCategory)) {
                listOf(defaultCategory) + availableCategories.filter { it != defaultCategory }
            } else {
                availableCategories
            }
        }

    // A file was chosen (either picked or prefilled): prepare its tree when possible.
    fun setPickedFile(uri: Uri) {
        if (viewModel.confirmingAdd.value) return
        viewModel.cancelFileSelection {
            pickedUri = uri.toString()
            deselected = emptySet()
            preparedMagnet = null
            filesUnavailableReason = null
            val bytes = readUriBytes(context, uri)
            if (bytes == null || !viewModel.prepareTorrentFileSelection(bytes)) {
                filesUnavailableReason = CommonR.string.files_unavailable
            }
        }
    }

    // OpenDocument (ACTION_OPEN_DOCUMENT) rather than GetContent: the latter returns RESULT_OK with
    // no URI on some providers, so a picked .torrent silently never reaches the screen.
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) setPickedFile(uri)
        }

    // Prefilled file: prepare it once on first composition (survives recreation via pickedUri).
    val prepareState = rememberSaveable { mutableStateOf(false) }
    if (prefillFileUri != null && !prepareState.value) {
        prepareState.value = true
        setPickedFile(prefillFileUri.toUri())
    }

    fun maybePrepareMagnet() {
        if (pickedUri != null || preparedMagnet != null) return
        val url = magnetText
        if (url.startsWith("magnet:") && linkValidator.isValid(url)) {
            filesUnavailableReason = null
            if (viewModel.prepareMagnetSelection(url)) {
                preparedMagnet = url
            } else {
                filesUnavailableReason = CommonR.string.files_unavailable
            }
        }
    }

    fun dismiss() {
        if (viewModel.confirmingAdd.value) return
        viewModel.cancelFileSelection(onDismiss)
    }

    fun changeSource(text: String) {
        if (preparedMagnet != null && preparedMagnet != text) {
            viewModel.cancelFileSelection {
                magnetText = text
                magnetError = false
                preparedMagnet = null
                deselected = emptySet()
                filesUnavailableReason = null
            }
        } else {
            magnetText = text
            magnetError = false
        }
    }

    fun onAdd() {
        if (viewModel.confirmingAdd.value) return
        val cat = category.takeIf { it.isNotBlank() }
        val sp = savePath.takeIf { it.isNotBlank() }
        viewModel.saveAddTorrentPrefs(autoTmm, paused)
        if (saveCategory) viewModel.saveDefaultCategory(cat)

        when {
            viewModel.hasPendingSelection -> {
                if (preparedMagnet != null && fileSelection?.tree == null) selectedTab = 1
                viewModel.confirmAdd(deselected, cat, sp, paused, autoTmm)
                return
            }
            pickedUri != null -> {
                val bytes = readUriBytes(context, pickedUri!!.toUri())
                if (bytes != null) {
                    viewModel.addTorrentFile(
                        bytes,
                        cat,
                        sp,
                        paused.takeIf { it },
                        autoTmm,
                    )
                } else {
                    Toast.makeText(context, noFileSelectedMessage, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            else -> {
                if (magnetText.isEmpty() || !linkValidator.isValid(magnetText)) {
                    magnetError = true
                    return
                }
                viewModel.addTorrentUrl(
                    magnetText,
                    cat,
                    sp,
                    paused.takeIf { it },
                    autoTmm,
                )
            }
        }
        onDismiss()
    }

    BackHandler { dismiss() }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                MediumTopAppBar(
                    title = { Text(stringResource(CommonR.string.add_torrent_title)) },
                    navigationIcon = {
                        IconButton(onClick = { dismiss() }, enabled = !confirming) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(CommonR.string.cancel),
                            )
                        }
                    },
                    actions = {
                        if (!confirming && selectedTab == 1 && !filesSearchOpen) {
                            IconButton(onClick = { filesSearchOpen = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription =
                                        stringResource(CommonR.string.content_description_search),
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onAdd() },
                            enabled =
                                !confirming &&
                                    !(preparedMagnet != null &&
                                        fileSelection?.let {
                                            it.tree == null && it.error == null
                                        } == true),
                            modifier = Modifier.padding(end = 16.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(
                                stringResource(
                                    if (
                                        confirmationError != null ||
                                            (viewModel.hasPendingSelection &&
                                                fileSelection?.error != null)
                                    )
                                        CommonR.string.retry_action
                                    else CommonR.string.add,
                                ),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            if (confirming) {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (confirmationError != null) {
                    Text(
                        confirmationError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(CommonR.string.tab_options)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            maybePrepareMagnet()
                        },
                        text = { Text(stringResource(CommonR.string.tab_files)) },
                    )
                }

                if (selectedTab == 1 && filesSearchOpen) {
                    ContentSearchField(
                        query = filesQuery,
                        onQueryChange = { filesQuery = it },
                        onClose = {
                            filesSearchOpen = false
                            filesQuery = ""
                        },
                    )
                }

                when (selectedTab) {
                    0 ->
                        OptionsTab(
                            fileChosen = pickedUri != null,
                            fileName =
                                pickedUri?.let { displayName(context, it.toUri()) }.orEmpty(),
                            hidePickButton = prefillFileUri != null,
                            magnetText = magnetText,
                            magnetError = magnetError,
                            onMagnetChange = ::changeSource,
                            onPaste = {
                                val text = ClipboardUtil.getClipboardText(context).trim()
                                if (text.isEmpty()) {
                                    Toast.makeText(
                                            context,
                                            clipboardEmptyMessage,
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                } else {
                                    changeSource(text)
                                }
                            },
                            onPickFile = { filePicker.launch(arrayOf(TORRENT_MIMETYPE)) },
                            categories = orderedCategories,
                            category = category,
                            categoryExpanded = categoryExpanded,
                            onCategoryChange = { category = it },
                            onCategoryExpandedChange = { categoryExpanded = it },
                            saveCategory = saveCategory,
                            onSaveCategoryChange = { saveCategory = it },
                            autoTmm = autoTmm,
                            onAutoTmmChange = { autoTmm = it },
                            savePath = savePath,
                            onSavePathChange = { savePath = it },
                            paused = paused,
                            onPausedChange = { paused = it },
                        )
                    1 ->
                        FilesTab(
                            current = fileSelection,
                            deselected = deselected,
                            filesUnavailableReason = filesUnavailableReason,
                            query = filesQuery,
                            onToggle = { item ->
                                val indices = item.allFileIndices()
                                deselected =
                                    if (indices.all { it in deselected }) {
                                        deselected - indices.toSet()
                                    } else {
                                        deselected + indices
                                    }
                            },
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsTab(
    fileChosen: Boolean,
    fileName: String,
    hidePickButton: Boolean,
    magnetText: String,
    magnetError: Boolean,
    onMagnetChange: (String) -> Unit,
    onPaste: () -> Unit,
    onPickFile: () -> Unit,
    categories: List<String>,
    category: String,
    categoryExpanded: Boolean,
    onCategoryChange: (String) -> Unit,
    onCategoryExpandedChange: (Boolean) -> Unit,
    saveCategory: Boolean,
    onSaveCategoryChange: (Boolean) -> Unit,
    autoTmm: Boolean,
    onAutoTmmChange: (Boolean) -> Unit,
    savePath: String,
    onSavePathChange: (String) -> Unit,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FormSection(
            title = stringResource(CommonR.string.torrent_source_section_title),
            icon = Icons.Filled.Link,
        ) {
            if (fileChosen) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(CommonR.string.selected_file)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = magnetText,
                    onValueChange = onMagnetChange,
                    label = { Text(stringResource(CommonR.string.enter_url)) },
                    placeholder = { Text(stringResource(CommonR.string.magnet_url_placeholder)) },
                    isError = magnetError,
                    supportingText =
                        if (magnetError) {
                            { Text(stringResource(CommonR.string.invalid_magnet_link)) }
                        } else null,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onPaste) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = stringResource(android.R.string.paste),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!hidePickButton) {
                    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(CommonR.string.pick_torrent_file))
                    }
                }
            }
        }

        FormSection(
            title = stringResource(CommonR.string.download_options_section_title),
            icon = Icons.Filled.Tune,
        ) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = onCategoryExpandedChange,
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = onCategoryChange,
                    label = { Text(stringResource(CommonR.string.category_pick_or_type_label)) },
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                val suggestions = categories.filter { it.isNotBlank() }
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { onCategoryExpandedChange(false) },
                    ) {
                        suggestions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onCategoryChange(option)
                                    onCategoryExpandedChange(false)
                                },
                            )
                        }
                    }
                }
            }

            FormSwitch(
                label = stringResource(CommonR.string.save_as_default_category_label),
                checked = saveCategory,
                onCheckedChange = onSaveCategoryChange,
            )
            FormSwitch(
                label = stringResource(CommonR.string.automatic_torrent_management_label),
                checked = autoTmm,
                onCheckedChange = onAutoTmmChange,
            )
            if (!autoTmm) {
                OutlinedTextField(
                    value = savePath,
                    onValueChange = onSavePathChange,
                    label = { Text(stringResource(CommonR.string.save_path_optional_label)) },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormSwitch(
                label = stringResource(CommonR.string.add_as_paused_label),
                checked = paused,
                onCheckedChange = onPausedChange,
            )
        }
    }
}

@Composable
private fun FilesTab(
    current: FileSelectionUiState?,
    deselected: Set<Int>,
    filesUnavailableReason: Int?,
    query: String,
    onToggle: (ContentTreeItem) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = if (current?.tree != null) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            current?.tree != null -> {
                val visible =
                    remember(current.tree, query) { filterContentTree(current.tree, query) }
                if (visible.isEmpty()) {
                    Text(
                        stringResource(CommonR.string.no_files_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    TorrentContentSelectionView(
                        nodes = visible,
                        deselected = deselected,
                        onToggle = onToggle,
                        modifier = Modifier.padding(top = 8.dp),
                        filterKey = query.trim(),
                    )
                }
            }
            current != null && current.error == null -> {
                CircularProgressIndicator()
                Text(
                    stringResource(CommonR.string.fetching_metadata),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            else ->
                Text(
                    current?.error
                        ?: stringResource(
                            filesUnavailableReason ?: CommonR.string.files_placeholder,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

// SAF document URIs expose the file name via OpenableColumns, not lastPathSegment (which is the
// opaque document id, e.g. "primary:Download/foo.torrent").
private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }
        .getOrNull()
        ?: uri.lastPathSegment
        ?: context.getString(CommonR.string.torrent_file_fallback_name)

private fun readUriBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
}
    .getOrNull()
