package dev.yashgarg.qbit.ui.dialogs

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.databinding.AddTorrentScreenBinding
import dev.yashgarg.qbit.ui.compose.TorrentContentSelectionView
import dev.yashgarg.qbit.ui.compose.allFileIndices
import dev.yashgarg.qbit.ui.server.ServerViewModel
import dev.yashgarg.qbit.ui.theme.QbitComposeTheme
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.validation.LinkValidator

/**
 * Full-screen add-torrent screen with two tabs: Options (link/file, category, save path, switches)
 * and Files (the torrent's content tree with checkboxes). The Files tab fills in instantly for a
 * `.torrent` (parsed locally) and shows a loading state for magnets while their metadata is fetched
 * via a hidden stopped add — cancelled again if the screen is dismissed.
 */
class AddTorrentDialog : DialogFragment() {
    private val linkValidator by lazy { LinkValidator() }

    // Shown via ServerFragment's childFragmentManager, so the parent fragment owns the VM.
    private val viewModel by
        viewModels<ServerViewModel>(ownerProducer = { requireParentFragment() })

    private var binding: AddTorrentScreenBinding? = null

    /** File indices the user unchecked in the Files tab. Compose state for recomposition. */
    private val deselected = mutableStateOf(setOf<Int>())
    /** Non-null when the Files tab can't offer selection for the current source. */
    private val filesUnavailableReason = mutableStateOf<Int?>(null)

    private var pickedUris: MutableList<Uri> = mutableListOf()
    /** The magnet url a prepare was started for, to detect edits invalidating it. */
    private var preparedMagnet: String? = null
    private var confirmed = false

    private val availableCategories: List<String>
        get() = arguments?.getStringArrayList(ARG_CATEGORIES) ?: emptyList()

    private val defaultAutoTmm: Boolean
        get() = arguments?.getBoolean(ARG_DEFAULT_AUTO_TMM, false) ?: false

    private val defaultPaused: Boolean
        get() = arguments?.getBoolean(ARG_DEFAULT_PAUSED, false) ?: false

    private val defaultCategory: String
        get() = arguments?.getString(ARG_DEFAULT_CATEGORY).orEmpty()

    private val prefillUrl: String?
        get() = arguments?.getString(ARG_PREFILL_URL)

    private val prefillFileUri: String?
        get() = arguments?.getString(ARG_PREFILL_FILE_URI)

    private val filePickerLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (!uris.isNullOrEmpty()) setPickedFiles(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)

        savedInstanceState?.let { state ->
            state.getIntArray(STATE_DESELECTED)?.let { deselected.value = it.toSet() }
            pickedUris =
                state
                    .getStringArrayList(STATE_PICKED_URIS)
                    .orEmpty()
                    .map(Uri::parse)
                    .toMutableList()
            preparedMagnet = state.getString(STATE_PREPARED_MAGNET)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = AddTorrentScreenBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        val options = binding.optionsView

        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_add) {
                onAddClicked()
                true
            } else false
        }

        binding.tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val filesTab = tab.position == 1
                    options.root.visibility = if (filesTab) View.GONE else View.VISIBLE
                    binding.filesView.visibility = if (filesTab) View.VISIBLE else View.GONE
                    if (filesTab) maybePrepareMagnet()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )

        binding.filesView.setContent {
            QbitComposeTheme { FilesTabContent() }
        }

        setUpOptionsTab()
    }

    private fun setUpOptionsTab() {
        val options = requireNotNull(binding).optionsView

        // Surface the saved default category first so it's the top suggestion, then prefill it.
        val orderedCategories =
            if (defaultCategory.isNotBlank() && availableCategories.contains(defaultCategory)) {
                listOf(defaultCategory) + availableCategories.filter { it != defaultCategory }
            } else {
                availableCategories
            }
        options.categoryActv.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                listOf("") + orderedCategories,
            )
        )
        if (defaultCategory.isNotBlank()) {
            options.categoryActv.setText(defaultCategory, false)
        }
        options.saveCategorySwitch.isChecked = defaultCategory.isNotBlank()

        options.autoTmmSwitch.setOnCheckedChangeListener { _, isChecked ->
            options.savePathTil.visibility = if (isChecked) View.GONE else View.VISIBLE
        }
        options.autoTmmSwitch.isChecked = defaultAutoTmm
        options.pausedSwitch.isChecked = defaultPaused

        options.magnetTiet.doAfterTextChanged {
            options.magnetTil.error = null
            // Editing the link invalidates a magnet that was already prepared (and added
            // stopped) for the Files tab.
            val prepared = preparedMagnet
            if (prepared != null && it?.toString() != prepared) {
                viewModel.cancelFileSelection()
                preparedMagnet = null
                deselected.value = emptySet()
            }
        }

        when {
            prefillUrl != null -> options.magnetTiet.setText(prefillUrl)
            prefillFileUri != null -> {
                options.pickFileBtn.visibility = View.GONE
                if (pickedUris.isEmpty()) {
                    setPickedFiles(listOf(Uri.parse(prefillFileUri)))
                } else {
                    showPickedFileNames()
                }
            }
            else -> {
                options.magnetTil.setEndIconOnClickListener {
                    val text = ClipboardUtil.getClipboardText(requireContext()).trim()
                    if (text.isEmpty()) {
                        Toast.makeText(
                                requireContext(),
                                getString(CommonR.string.clipboard_empty),
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    } else {
                        options.magnetTiet.setText(text)
                        options.magnetTiet.setSelection(text.length)
                    }
                }
            }
        }
        if (prefillFileUri == null && pickedUris.isNotEmpty()) showPickedFileNames()

        // The Storage Access Framework picker grants read access to the chosen files, so no
        // storage permission is needed.
        options.pickFileBtn.setOnClickListener { filePickerLauncher.launch(TORRENT_MIMETYPE) }
    }

    /** A file (or several) was chosen: display it and prepare the Files tab when possible. */
    private fun setPickedFiles(uris: List<Uri>) {
        pickedUris = uris.toMutableList()
        deselected.value = emptySet()
        preparedMagnet = null
        filesUnavailableReason.value = null
        showPickedFileNames()

        when {
            uris.size > 1 -> {
                viewModel.cancelFileSelection()
                filesUnavailableReason.value = CommonR.string.files_multi_unavailable
            }
            else -> {
                val bytes = readUriBytes(uris.first())
                if (bytes == null || !viewModel.prepareTorrentFileSelection(bytes)) {
                    viewModel.cancelFileSelection()
                    filesUnavailableReason.value = CommonR.string.files_unavailable
                }
            }
        }
    }

    private fun showPickedFileNames() {
        val options = requireNotNull(binding).optionsView
        options.magnetTil.hint = getString(CommonR.string.selected_file)
        options.magnetTil.isEndIconVisible = false
        options.magnetTiet.setText(
            pickedUris.joinToString(", ") {
                it.lastPathSegment ?: getString(CommonR.string.torrent_file_fallback_name)
            }
        )
        options.magnetTiet.isFocusable = false
        options.magnetTiet.isClickable = false
    }

    private fun readUriBytes(uri: Uri): ByteArray? =
        runCatching {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            .getOrNull()

    /** Called when the Files tab opens with a magnet link in the field. */
    private fun maybePrepareMagnet() {
        if (pickedUris.isNotEmpty() || preparedMagnet != null) return
        val url = requireNotNull(binding).optionsView.magnetTiet.text?.toString().orEmpty()
        if (url.startsWith("magnet:") && linkValidator.isValid(url)) {
            filesUnavailableReason.value = null
            if (viewModel.prepareMagnetSelection(url)) {
                preparedMagnet = url
            } else {
                filesUnavailableReason.value = CommonR.string.files_unavailable
            }
        }
    }

    @Composable
    private fun FilesTabContent() {
        val state by viewModel.fileSelection.collectAsState()
        val current = state
        val unavailable = filesUnavailableReason.value

        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement =
                if (current?.tree != null) Arrangement.Top else Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                current?.tree != null ->
                    TorrentContentSelectionView(
                        nodes = current.tree,
                        deselected = deselected.value,
                        onToggle = ::toggle,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                current != null -> {
                    CircularProgressIndicator()
                    Text(
                        getString(CommonR.string.fetching_metadata),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                else ->
                    Text(
                        getString(unavailable ?: CommonR.string.files_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }

    // Toggling a folder flips everything beneath it: all selected -> exclude all, else include all.
    private fun toggle(item: ContentTreeItem) {
        val indices = item.allFileIndices()
        val current = deselected.value
        deselected.value =
            if (indices.all { it in current }) current - indices.toSet() else current + indices
    }

    private fun onAddClicked() {
        val options = requireNotNull(binding).optionsView
        val category = options.categoryActv.text?.toString()?.takeIf { it.isNotBlank() }
        val savePath = options.savePathTiet.text?.toString()?.takeIf { it.isNotBlank() }
        val paused = options.pausedSwitch.isChecked
        val autoTmm = options.autoTmmSwitch.isChecked

        viewModel.saveAddTorrentPrefs(autoTmm, paused)
        if (options.saveCategorySwitch.isChecked) {
            viewModel.saveDefaultCategory(category)
        }

        when {
            viewModel.hasPendingSelection ->
                viewModel.confirmAdd(deselected.value, category, savePath, paused, autoTmm)
            pickedUris.isNotEmpty() ->
                pickedUris.forEach { uri ->
                    val bytes = readUriBytes(uri)
                    if (bytes != null) {
                        viewModel.addTorrentFile(
                            bytes,
                            category,
                            savePath,
                            paused.takeIf { it },
                            autoTmm.takeIf { it },
                        )
                    } else {
                        Toast.makeText(
                                requireContext(),
                                getString(CommonR.string.no_file_selected),
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            else -> {
                val magnetUri = options.magnetTiet.text?.toString().orEmpty()
                if (magnetUri.isEmpty() || !linkValidator.isValid(magnetUri)) {
                    options.magnetTil.error = getString(CommonR.string.invalid_magnet_link)
                    return
                }
                viewModel.addTorrentUrl(
                    magnetUri,
                    category,
                    savePath,
                    paused.takeIf { it },
                    autoTmm.takeIf { it },
                )
            }
        }

        confirmed = true
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Dismissed without adding: abort a prepared source (removes a metadata-fetch magnet).
        if (!confirmed) viewModel.cancelFileSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_DESELECTED, deselected.value.toIntArray())
        outState.putStringArrayList(
            STATE_PICKED_URIS,
            ArrayList(pickedUris.map(Uri::toString)),
        )
        outState.putString(STATE_PREPARED_MAGNET, preparedMagnet)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        fun newInstance(
            availableCategories: List<String> = emptyList(),
            defaultAutoTmm: Boolean = false,
            defaultPaused: Boolean = false,
            defaultCategory: String = "",
            prefillUrl: String? = null,
            prefillFileUri: String? = null,
        ): AddTorrentDialog =
            AddTorrentDialog().apply {
                arguments =
                    Bundle().apply {
                        putStringArrayList(ARG_CATEGORIES, ArrayList(availableCategories))
                        putBoolean(ARG_DEFAULT_AUTO_TMM, defaultAutoTmm)
                        putBoolean(ARG_DEFAULT_PAUSED, defaultPaused)
                        putString(ARG_DEFAULT_CATEGORY, defaultCategory)
                        putString(ARG_PREFILL_URL, prefillUrl)
                        putString(ARG_PREFILL_FILE_URI, prefillFileUri)
                    }
            }

        const val TAG = "AddTorrentDialogFragment"
        private const val ARG_CATEGORIES = "arg_categories"
        private const val ARG_DEFAULT_AUTO_TMM = "arg_default_auto_tmm"
        private const val ARG_DEFAULT_PAUSED = "arg_default_paused"
        private const val ARG_DEFAULT_CATEGORY = "arg_default_category"
        private const val ARG_PREFILL_URL = "arg_prefill_url"
        private const val ARG_PREFILL_FILE_URI = "arg_prefill_file_uri"
        private const val STATE_DESELECTED = "state_deselected"
        private const val STATE_PICKED_URIS = "state_picked_uris"
        private const val STATE_PREPARED_MAGNET = "state_prepared_magnet"
        const val TORRENT_MIMETYPE = "application/x-bittorrent"
    }
}
