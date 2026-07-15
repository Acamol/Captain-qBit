package dev.yashgarg.qbit.ui.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.TorrentContentSelectionView
import dev.yashgarg.qbit.ui.compose.allFileIndices
import dev.yashgarg.qbit.ui.server.ServerViewModel
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

    var magnetText by rememberSaveable { mutableStateOf(prefillUrl.orEmpty()) }
    var pickedUri by rememberSaveable { mutableStateOf(prefillFileUri) }
    var preparedMagnet by rememberSaveable { mutableStateOf<String?>(null) }
    var deselected by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet()) }
    var filesUnavailableReason by rememberSaveable { mutableStateOf<Int?>(null) }
    var magnetError by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

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
        pickedUri = uri.toString()
        deselected = emptySet()
        preparedMagnet = null
        filesUnavailableReason = null
        val bytes = readUriBytes(context, uri)
        if (bytes == null || !viewModel.prepareTorrentFileSelection(bytes)) {
            viewModel.cancelFileSelection()
            filesUnavailableReason = CommonR.string.files_unavailable
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
        // Dismissed without adding: abort a prepared source (removes a metadata-fetch magnet).
        if (!confirmed) viewModel.cancelFileSelection()
        onDismiss()
    }

    fun onAdd() {
        val cat = category.takeIf { it.isNotBlank() }
        val sp = savePath.takeIf { it.isNotBlank() }
        viewModel.saveAddTorrentPrefs(autoTmm, paused)
        if (saveCategory) viewModel.saveDefaultCategory(cat)

        when {
            viewModel.hasPendingSelection ->
                viewModel.confirmAdd(deselected, cat, sp, paused, autoTmm)
            pickedUri != null -> {
                val bytes = readUriBytes(context, pickedUri!!.toUri())
                if (bytes != null) {
                    viewModel.addTorrentFile(
                        bytes,
                        cat,
                        sp,
                        paused.takeIf { it },
                        autoTmm.takeIf { it },
                    )
                } else {
                    Toast.makeText(
                            context,
                            context.getString(CommonR.string.no_file_selected),
                            Toast.LENGTH_SHORT,
                        )
                        .show()
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
                    autoTmm.takeIf { it },
                )
            }
        }
        confirmed = true
        dismiss()
    }

    BackHandler { dismiss() }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(CommonR.string.add_torrent_title)) },
                    navigationIcon = {
                        IconButton(onClick = { dismiss() }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(onClick = { onAdd() }) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(
                                stringResource(CommonR.string.add),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    },
                )
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
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

                when (selectedTab) {
                    0 ->
                        OptionsTab(
                            fileChosen = pickedUri != null,
                            fileName =
                                pickedUri?.let { displayName(context, it.toUri()) }.orEmpty(),
                            hidePickButton = prefillFileUri != null,
                            magnetText = magnetText,
                            magnetError = magnetError,
                            onMagnetChange = {
                                magnetText = it
                                magnetError = false
                                // Editing the link invalidates a magnet already prepared for Files.
                                val prepared = preparedMagnet
                                if (prepared != null && it != prepared) {
                                    viewModel.cancelFileSelection()
                                    preparedMagnet = null
                                    deselected = emptySet()
                                }
                            },
                            onPaste = {
                                val text = ClipboardUtil.getClipboardText(context).trim()
                                if (text.isEmpty()) {
                                    Toast.makeText(
                                            context,
                                            context.getString(CommonR.string.clipboard_empty),
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                } else {
                                    magnetText = text
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
                            viewModel = viewModel,
                            deselected = deselected,
                            filesUnavailableReason = filesUnavailableReason,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                placeholder = { Text("http://, https://, magnet: or bc://bt/") },
                isError = magnetError,
                supportingText =
                    if (magnetError) {
                        { Text(stringResource(CommonR.string.invalid_magnet_link)) }
                    } else null,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
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

        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = onCategoryExpandedChange,
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = onCategoryChange,
                label = { Text("Category (pick or type a new one)") },
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

        SwitchRow(
            label = "Save as default category",
            checked = saveCategory,
            onCheckedChange = onSaveCategoryChange,
        )
        SwitchRow(
            label = "Automatic torrent management",
            checked = autoTmm,
            onCheckedChange = onAutoTmmChange,
        )
        if (!autoTmm) {
            OutlinedTextField(
                value = savePath,
                onValueChange = onSavePathChange,
                label = { Text("Save path (optional)") },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(label = "Add as paused", checked = paused, onCheckedChange = onPausedChange)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FilesTab(
    viewModel: ServerViewModel,
    deselected: Set<Int>,
    filesUnavailableReason: Int?,
    onToggle: (ContentTreeItem) -> Unit,
) {
    val state by viewModel.fileSelection.collectAsState()
    val current = state

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = if (current?.tree != null) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            current?.tree != null ->
                TorrentContentSelectionView(
                    nodes = current.tree,
                    deselected = deselected,
                    onToggle = onToggle,
                    modifier = Modifier.padding(top = 8.dp),
                )
            current != null -> {
                CircularProgressIndicator()
                Text(
                    stringResource(CommonR.string.fetching_metadata),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            else ->
                Text(
                    stringResource(filesUnavailableReason ?: CommonR.string.files_placeholder),
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

private fun readUriBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
