package dev.yashgarg.qbit.ui.server

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.Selection
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.ServerFragmentBinding
import dev.yashgarg.qbit.ui.dialogs.AddTorrentDialog
import dev.yashgarg.qbit.ui.dialogs.RemoveTorrentDialog
import dev.yashgarg.qbit.ui.server.adapter.TorrentListAdapter
import dev.yashgarg.qbit.utils.TorrentHashUtil
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.validation.LinkValidator
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ServerFragment : Fragment(R.layout.server_fragment) {
    private val binding by viewBinding(ServerFragmentBinding::bind)
    private val viewModel by viewModels<ServerViewModel>()
    private val linkValidator by lazy { LinkValidator() }
    private var selectedItems: Selection<String>? = null
    private var clearSelectionCallback: OnBackPressedCallback? = null

    private var lastSortOption = SortOption.NAME
    private var lastSortDir = SortDirection.ASC
    private var lastSearchQuery = ""
    private var lastCategory: String? = null
    private var lastFilter = StateFilter.ALL
    private var lastTracker: String? = null
    private var lastTags: Set<String> = emptySet()
    private var pendingScrollToTop = false
    private var pendingListReset = false

    // View-scoped: created in onViewCreated, released in onDestroyView. The adapter binds a
    // SelectionTracker (which registers a non-removable adapter observer), so keeping it alive
    // across view recreations would retain the destroyed view. Recreating it per view avoids that.
    private var torrentListAdapter: TorrentListAdapter? = null

    private val onNewIntentListener =
        Consumer<Intent> { intent ->
            val bundle = bundleOf(MainActivity.TORRENT_INTENT_KEY to intent?.data.toString())
            handleAddIntent(bundle)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        activity?.addOnNewIntentListener(onNewIntentListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        torrentListAdapter = TorrentListAdapter()
        clearSelectionCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    torrentListAdapter?.clearSelection()
                }
            }
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                requireNotNull(clearSelectionCallback),
            )
        setupHandlers()
        observeFlows()
        setupDialogResultListener()
    }

    override fun onDestroyView() {
        // Drop the adapter (and its SelectionTracker) so this view isn't retained. Don't touch
        // `binding` here: with the exit transition, onDestroyView runs after the view lifecycle
        // is already DESTROYED (binding cleared). onStop() already detached the adapter from the
        // RV.
        lastDrawerCategories = emptyList()
        lastDrawerTrackers = emptyList()
        lastDrawerTags = emptyList()
        lastDrawerState = null
        lastFilterUntagged = false
        torrentListAdapter = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.removeOnNewIntentListener(onNewIntentListener)
    }

    override fun onStop() {
        super.onStop()
        binding.refreshLayout.isEnabled = false
        binding.torrentRv.adapter = null
    }

    override fun onResume() {
        super.onResume()
        binding.refreshLayout.isEnabled = true
        binding.torrentRv.adapter = torrentListAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupDialogResultListener() {
        childFragmentManager.apply {
            setFragmentResultListener(RemoveTorrentDialog.REMOVE_TORRENT_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val deleteFiles = bundle.getBoolean(RemoveTorrentDialog.TORRENT_KEY)
                Log.d(this.javaClass.simpleName, "Selection: ${selectedItems?.toList()}")
                selectedItems?.toList()?.let {
                    viewModel.removeTorrents(it, deleteFiles)
                    torrentListAdapter?.notifyDataSetChanged()
                }
                binding.bottomBar.menu.findItem(R.id.delete_selection).isVisible = false
            }

            setFragmentResultListener(AddTorrentDialog.ADD_TORRENT_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val url = bundle.getString(AddTorrentDialog.TORRENT_KEY)
                val category = bundle.getString(AddTorrentDialog.CATEGORY_KEY)
                val savePath = bundle.getString(AddTorrentDialog.SAVE_PATH_KEY)
                val paused = bundle.getBoolean(AddTorrentDialog.PAUSED_KEY, false)
                val autoTmm = bundle.getBoolean(AddTorrentDialog.AUTO_TMM_KEY, false)
                viewModel.saveAddTorrentPrefs(autoTmm, paused)
                if (bundle.getBoolean(AddTorrentDialog.SAVE_CATEGORY_DEFAULT_KEY, false)) {
                    viewModel.saveDefaultCategory(category)
                }
                viewModel.addTorrentUrl(
                    requireNotNull(url),
                    category,
                    savePath,
                    paused.takeIf { it },
                    autoTmm.takeIf { it },
                )
            }

            @Suppress("UNCHECKED_CAST")
            setFragmentResultListener(AddTorrentDialog.ADD_TORRENT_FILE_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val uris =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bundle.getParcelableArrayList(AddTorrentDialog.TORRENT_KEY, Uri::class.java)
                    } else {
                        bundle.getStringArrayList(AddTorrentDialog.TORRENT_KEY) as ArrayList<Uri>
                    }
                val category = bundle.getString(AddTorrentDialog.CATEGORY_KEY)
                val savePath = bundle.getString(AddTorrentDialog.SAVE_PATH_KEY)
                val paused = bundle.getBoolean(AddTorrentDialog.PAUSED_KEY, false)
                val autoTmm = bundle.getBoolean(AddTorrentDialog.AUTO_TMM_KEY, false)
                viewModel.saveAddTorrentPrefs(autoTmm, paused)
                if (bundle.getBoolean(AddTorrentDialog.SAVE_CATEGORY_DEFAULT_KEY, false)) {
                    viewModel.saveDefaultCategory(category)
                }

                uris?.forEach { uri ->
                    requireContext().contentResolver.openInputStream(uri).use { stream ->
                        viewModel.addTorrentFile(
                            requireNotNull(stream).readBytes(),
                            category,
                            savePath,
                            paused.takeIf { it },
                            autoTmm.takeIf { it },
                        )
                    }
                }
            }
        }
    }

    private fun handleAddIntent(bundle: Bundle?) {
        val uri: String? = (bundle ?: arguments)?.getString(MainActivity.TORRENT_INTENT_KEY)
        (bundle ?: arguments)?.clear()
        if (uri.isNullOrEmpty()) return
        if (childFragmentManager.findFragmentByTag(AddTorrentDialog.TAG) != null) return

        val isLink = linkValidator.isValid(uri)
        val isFile = uri.startsWith("content://") || uri.startsWith("file://")
        if (!isLink && !isFile) return

        viewLifecycleOwner.lifecycleScope.launch {
            // Skip the dialog entirely if the torrent is already in the client.
            val incomingHash =
                if (isLink) {
                    TorrentHashUtil.infoHashFromMagnet(uri)
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                                requireContext()
                                    .contentResolver
                                    .openInputStream(Uri.parse(uri))
                                    ?.use { it.readBytes() }
                            }
                            .getOrNull()
                            ?.let(TorrentHashUtil::infoHashFromTorrent)
                    }
                }
            val existing = viewModel.uiState.value.data?.torrents?.keys.orEmpty()
            if (incomingHash != null && existing.contains(incomingHash)) {
                Toast.makeText(requireContext(), "Torrent already exists", Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }

            val prefs = viewModel.addTorrentPrefs.value
            val categories = viewModel.uiState.value.availableCategories
            val dialog =
                if (isLink) {
                    AddTorrentDialog.newInstance(
                        availableCategories = categories,
                        defaultAutoTmm = prefs.addTorrentAutoTmm,
                        defaultPaused = prefs.addTorrentPaused,
                        defaultCategory = prefs.addTorrentCategory,
                        prefillUrl = uri,
                    )
                } else {
                    AddTorrentDialog.newInstance(
                        availableCategories = categories,
                        defaultAutoTmm = prefs.addTorrentAutoTmm,
                        defaultPaused = prefs.addTorrentPaused,
                        defaultCategory = prefs.addTorrentCategory,
                        prefillFileUri = uri,
                    )
                }
            dialog.show(childFragmentManager, AddTorrentDialog.TAG)
        }
    }

    private var lastDrawerCategories: List<String?> = emptyList()
    private var lastDrawerTrackers: List<String?> = emptyList()
    private var lastDrawerTags: List<String> = emptyList()
    private var lastDrawerState: ServerState? = null
    private var lastFilterUntagged = false

    private fun setupHandlers() {
        with(binding) {
            bottomBar.setNavigationOnClickListener { drawerLayout.openDrawer(Gravity.START) }
            clearFiltersBtn.setOnClickListener {
                viewModel.clearFilters()
                drawerLayout.closeDrawer(Gravity.START)
            }
            torrentListAdapter?.onItemClick = { hash ->
                val action =
                    ServerFragmentDirections.actionServerFragmentToTorrentInfoFragment(hash)
                findNavController().navigate(action)
            }

            torrentRv.itemAnimator = null
            torrentRv.adapter = torrentListAdapter

            searchEt.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        viewModel.setSearchQuery(s?.toString() ?: "")
                    }
                }
            )

            torrentListAdapter?.makeSelectable(torrentRv) { selection ->
                selectedItems = selection
                val hasSelection = selection.size() > 0
                clearSelectionCallback?.isEnabled = hasSelection

                bottomBar.menu.apply {
                    findItem(R.id.manage_tags).isVisible = !hasSelection
                    findItem(R.id.manage_categories).isVisible = !hasSelection

                    findItem(R.id.category_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            showBulkCategoryPicker()
                            true
                        }
                    }

                    findItem(R.id.tags_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            showBulkTagsPicker()
                            true
                        }
                    }

                    findItem(R.id.delete_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            RemoveTorrentDialog.newInstance()
                                .show(childFragmentManager, RemoveTorrentDialog.TAG)
                            true
                        }
                    }

                    findItem(R.id.pause_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            selectedItems?.toList()?.let { hashes ->
                                viewModel.toggleTorrentsState(true, hashes)
                            }
                            close()
                            true
                        }
                    }

                    findItem(R.id.resume_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            selectedItems?.toList()?.let { hashes ->
                                viewModel.toggleTorrentsState(false, hashes)
                            }
                            close()
                            true
                        }
                    }
                }
            }

            refreshLayout.setOnRefreshListener { viewModel.refresh() }

            addTorrentFab.setOnClickListener {
                val prefs = viewModel.addTorrentPrefs.value
                AddTorrentDialog.newInstance(
                        viewModel.uiState.value.availableCategories,
                        defaultAutoTmm = prefs.addTorrentAutoTmm,
                        defaultPaused = prefs.addTorrentPaused,
                        defaultCategory = prefs.addTorrentCategory,
                    )
                    .show(childFragmentManager, AddTorrentDialog.TAG)
            }

            bottomBar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search -> {
                        val visible = searchLayout.visibility == View.VISIBLE
                        if (visible) {
                            searchEt.setText("")
                            viewModel.setSearchQuery("")
                            searchLayout.visibility = View.GONE
                            val imm = requireContext().getSystemService<InputMethodManager>()
                            imm?.hideSoftInputFromWindow(searchEt.windowToken, 0)
                        } else {
                            searchLayout.visibility = View.VISIBLE
                            searchEt.requestFocus()
                            val imm = requireContext().getSystemService<InputMethodManager>()
                            imm?.showSoftInput(searchEt, InputMethodManager.SHOW_IMPLICIT)
                        }
                        true
                    }
                    R.id.sort_list -> {
                        showSortPicker()
                        true
                    }
                    R.id.speed_toggle -> {
                        viewLifecycleOwner.lifecycleScope.launch { viewModel.toggleSpeedLimits() }
                        true
                    }
                    R.id.edit_server -> {
                        findNavController().navigate(R.id.action_serverFragment_to_configFragment)
                        true
                    }
                    R.id.manage_tags -> {
                        showManageTagsDialog()
                        true
                    }
                    R.id.manage_categories -> {
                        showManageCategoriesDialog()
                        true
                    }
                    R.id.about -> {
                        findNavController().navigate(R.id.action_serverFragment_to_versionFragment)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun observeFlows() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.sortedTorrents
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { torrents ->
                if (torrents == null) return@onEach
                with(binding) {
                    if (torrents.isEmpty()) {
                        emptyTv.visibility = View.VISIBLE
                        torrentRv.visibility = View.GONE
                    } else {
                        emptyTv.visibility = View.GONE
                        torrentRv.visibility = View.VISIBLE
                        val scroll = pendingScrollToTop
                        val reset = pendingListReset
                        pendingScrollToTop = false
                        pendingListReset = false
                        if (reset) {
                            torrentListAdapter?.submitList(emptyList()) {
                                torrentListAdapter?.submitList(torrents) {
                                    if (scroll) torrentRv.scrollToPosition(0)
                                }
                            }
                        } else {
                            torrentListAdapter?.submitList(torrents) {
                                if (scroll) torrentRv.scrollToPosition(0)
                            }
                        }
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.status
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.intent
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { handleAddIntent(null) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun sidebarItem(
        text: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val indicatorW = (3 * density).toInt()
        val itemPadV = (10 * density).toInt()
        val gapH = (12 * density).toInt()
        val seedColor = ctx.getColor(R.color.md_theme_dark_seed)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(
                View(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(indicatorW, MATCH_PARENT).also {
                            it.marginEnd = gapH
                        }
                    setBackgroundColor(if (selected) seedColor else Color.TRANSPARENT)
                }
            )
            addView(
                TextView(ctx).apply {
                    this.text = text
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    setPadding(0, itemPadV, 16.dpPx, itemPadV)
                    textSize = 14f
                    setTextColor(if (selected) Color.WHITE else 0x80FFFFFF.toInt())
                    if (selected) setTypeface(typeface, Typeface.BOLD)
                }
            )
        }
    }

    private val Int.dpPx
        get() = (this * resources.displayMetrics.density).toInt()

    private fun trackerBaseDomain(url: String): String {
        return try {
            val host = Uri.parse(url).host ?: return url
            val parts = host.split(".")
            if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
        } catch (_: Exception) {
            url
        }
    }

    private fun updateDrawerContent(state: ServerState) {
        with(binding) {
            val categories = listOf(null) + state.availableCategories
            val trackers = listOf(null) + state.availableTrackers
            val tags = state.availableTags

            // Status — always rebuilt when selection changes (small fixed list)
            statusItemsContainer.removeAllViews()
            StateFilter.entries.forEach { filter ->
                statusItemsContainer.addView(
                    sidebarItem(filter.label, filter == state.selectedFilter) {
                        viewModel.setFilter(filter)
                    }
                )
            }

            // Category
            if (categories != lastDrawerCategories) {
                lastDrawerCategories = categories
                val hasCategories = state.availableCategories.isNotEmpty()
                drawerCategoryHeader.visibility = if (hasCategories) View.VISIBLE else View.GONE
                drawerCategoryDivider.visibility = if (hasCategories) View.VISIBLE else View.GONE
                categoryItemsContainer.removeAllViews()
                categories.forEach { category ->
                    categoryItemsContainer.addView(
                        sidebarItem(category ?: "All", category == state.selectedCategory) {
                            viewModel.setCategory(category)
                        }
                    )
                }
            } else if (state.selectedCategory != lastDrawerState?.selectedCategory) {
                for (i in 0 until categoryItemsContainer.childCount) {
                    rebuildSidebarItemSelection(
                        categoryItemsContainer.getChildAt(i),
                        categories.getOrNull(i),
                        state.selectedCategory,
                    )
                }
            }

            // Tracker
            if (trackers != lastDrawerTrackers) {
                lastDrawerTrackers = trackers
                val hasTrackers = state.availableTrackers.isNotEmpty()
                drawerTrackerHeader.visibility = if (hasTrackers) View.VISIBLE else View.GONE
                drawerTagsDivider.visibility = if (hasTrackers) View.VISIBLE else View.GONE
                trackerItemsContainer.removeAllViews()
                trackers.forEach { tracker ->
                    val label = if (tracker != null) trackerBaseDomain(tracker) else "All"
                    trackerItemsContainer.addView(
                        sidebarItem(label, tracker == state.selectedTracker) {
                            viewModel.setTracker(tracker)
                        }
                    )
                }
            } else if (state.selectedTracker != lastDrawerState?.selectedTracker) {
                for (i in 0 until trackerItemsContainer.childCount) {
                    rebuildSidebarItemSelection(
                        trackerItemsContainer.getChildAt(i),
                        trackers.getOrNull(i),
                        state.selectedTracker,
                    )
                }
            }

            // Tags — always visible; "All" and "Untagged" are permanent entries
            val tagsStateChanged =
                tags != lastDrawerTags ||
                    state.selectedTags != lastDrawerState?.selectedTags ||
                    state.filterUntagged != lastDrawerState?.filterUntagged
            if (tagsStateChanged) {
                lastDrawerTags = tags
                drawerTagsHeader.visibility = View.VISIBLE
                tagsContainer.removeAllViews()
                val noneSelected = !state.filterUntagged && state.selectedTags.isEmpty()
                tagsContainer.addView(
                    sidebarItem("All", noneSelected) { viewModel.setFilterUntagged(false) }
                )
                tagsContainer.addView(
                    sidebarItem("Untagged", state.filterUntagged) {
                        viewModel.setFilterUntagged(true)
                    }
                )
                tags.forEach { tag ->
                    tagsContainer.addView(
                        sidebarItem(tag, state.selectedTags.contains(tag)) {
                            viewModel.toggleTag(tag)
                        }
                    )
                }
            }

            lastDrawerState = state
        }
    }

    private fun rebuildSidebarItemSelection(
        itemView: View,
        itemValue: Any?,
        selectedValue: Any?,
    ) {
        val selected = itemValue == selectedValue
        val seedColor = requireContext().getColor(R.color.md_theme_dark_seed)
        (itemView as? LinearLayout)?.let { row ->
            row.getChildAt(0)?.setBackgroundColor(if (selected) seedColor else Color.TRANSPARENT)
            (row.getChildAt(1) as? TextView)?.apply {
                setTextColor(if (selected) Color.WHITE else 0x80FFFFFF.toInt())
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    private fun showBulkCategoryPicker() {
        val state = viewModel.uiState.value
        val options = listOf("") + state.availableCategories
        val labels = options.map { it.ifBlank { "None" } }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set category")
            .setSingleChoiceItems(labels, -1) { dialog, which ->
                selectedItems?.toList()?.let { hashes ->
                    viewModel.bulkSetCategory(hashes, options[which])
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBulkTagsPicker() {
        val hashes = selectedItems?.toList()?.takeIf { it.isNotEmpty() } ?: return
        val state = viewModel.uiState.value
        val tags = state.availableTags
        if (tags.isEmpty()) {
            showCreateTagForSelectionDialog(hashes)
            return
        }
        val torrentTags =
            hashes.mapNotNull { state.data?.torrents?.get(it) }.flatMap { it.tags }.toSet()
        val initialChecked = BooleanArray(tags.size) { torrentTags.contains(tags[it]) }
        val checked = initialChecked.copyOf()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set tags")
            .setMultiChoiceItems(tags.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val toAdd = tags.filterIndexed { i, _ -> checked[i] && !initialChecked[i] }
                val toRemove = tags.filterIndexed { i, _ -> !checked[i] && initialChecked[i] }
                if (toAdd.isNotEmpty()) viewModel.bulkAddTags(hashes, toAdd)
                if (toRemove.isNotEmpty()) viewModel.bulkRemoveTags(hashes, toRemove)
            }
            .setNeutralButton("New tag…") { _, _ -> showCreateTagForSelectionDialog(hashes) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateTagForSelectionDialog(hashes: List<String>) {
        val view = layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Tag name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New tag")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableTags.contains(name) ->
                        til?.error = "Tag already exists"
                    else -> {
                        viewModel.bulkAddTags(hashes, listOf(name))
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    private fun showManageTagsDialog() {
        val tags = viewModel.uiState.value.availableTags
        val marked = BooleanArray(tags.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage tags")
            .setMultiChoiceItems(tags.toTypedArray(), marked) { _, which, isChecked ->
                marked[which] = isChecked
            }
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = tags.filterIndexed { i, _ -> marked[i] }
                if (toDelete.isNotEmpty()) viewModel.deleteTags(toDelete)
                else Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("New tag…") { _, _ -> showCreateNewTagDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateNewTagDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Tag name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New tag")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableTags.contains(name) ->
                        til?.error = "Tag already exists"
                    else -> {
                        viewModel.createTag(name)
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    private fun showManageCategoriesDialog() {
        val categories = viewModel.uiState.value.availableCategories
        val marked = BooleanArray(categories.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage categories")
            .setMultiChoiceItems(categories.toTypedArray(), marked) { _, which, isChecked ->
                marked[which] = isChecked
            }
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = categories.filterIndexed { i, _ -> marked[i] }
                if (toDelete.isNotEmpty()) viewModel.deleteCategories(toDelete)
                else Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("New category…") { _, _ -> showCreateNewCategoryDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateNewCategoryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Category name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New category")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableCategories.contains(name) ->
                        til?.error = "Category already exists"
                    else -> {
                        viewModel.createCategory(name)
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    private fun showSortPicker() {
        val state = viewModel.uiState.value
        val options = SortOption.entries
        val labels =
            options.map { option ->
                when {
                    option != state.sortOption -> option.label
                    state.sortDirection == SortDirection.ASC -> "↑ ${option.label}"
                    else -> "↓ ${option.label}"
                }
            }
        val checkedIndex = options.indexOf(state.sortOption)

        val dirLabel =
            if (state.sortDirection == SortDirection.ASC) "↑ Ascending" else "↓ Descending"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort by")
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                viewModel.setSort(options[which])
                dialog.dismiss()
            }
            .setNeutralButton(dirLabel) { _, _ -> viewModel.toggleSortDirection() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun render(state: ServerState) {
        with(binding) {
            val sortIcon = bottomBar.menu.findItem(R.id.sort_list)
            sortIcon?.icon?.alpha =
                if (state.sortOption != SortOption.NAME || state.sortDirection != SortDirection.ASC)
                    255
                else 128

            val sortChanged =
                state.sortOption != lastSortOption || state.sortDirection != lastSortDir
            val searchChanged = state.searchQuery != lastSearchQuery
            val filterChanged =
                state.selectedCategory != lastCategory ||
                    state.selectedFilter != lastFilter ||
                    state.selectedTracker != lastTracker ||
                    state.selectedTags != lastTags ||
                    state.filterUntagged != lastFilterUntagged
            if (
                (sortChanged || searchChanged || filterChanged) &&
                    !state.dataLoading &&
                    !state.hasError
            ) {
                pendingScrollToTop = true
                pendingListReset = true
            }
            lastSortOption = state.sortOption
            lastSortDir = state.sortDirection
            lastSearchQuery = state.searchQuery
            lastCategory = state.selectedCategory
            lastFilter = state.selectedFilter
            lastTracker = state.selectedTracker
            lastTags = state.selectedTags
            lastFilterUntagged = state.filterUntagged

            updateDrawerContent(state)
            updateFilterChips(state)

            if (state.hasError) {
                listLoader.visibility = View.GONE
                speedTv.visibility = View.GONE
                errorTv.text =
                    state.error?.message
                        ?: requireContext().getString(dev.yashgarg.qbit.common.R.string.error)
                errorTv.visibility = View.VISIBLE
                torrentRv.visibility = View.GONE
                refreshLayout.isRefreshing = false
                emptyTv.visibility = View.GONE
            } else if (!state.dataLoading) {
                errorTv.visibility = View.GONE
                listLoader.visibility = View.GONE

                val serverState = state.data?.serverState
                if (serverState != null) {
                    speedTv.visibility = View.VISIBLE
                    speedTv.text =
                        "↓ ${serverState.dlInfoSpeed.toHumanReadable()}/s  ↑ ${serverState.upInfoSpeed.toHumanReadable()}/s"
                } else {
                    speedTv.visibility = View.GONE
                }

                refreshLayout.isRefreshing = false
            }
        }
    }

    private fun updateFilterChips(state: ServerState) {
        with(binding) {
            filterChipGroup.removeAllViews()

            fun addChip(label: String, onClose: () -> Unit) {
                val chip =
                    Chip(requireContext()).apply {
                        text = label
                        isCloseIconVisible = true
                        setOnCloseIconClickListener { onClose() }
                    }
                filterChipGroup.addView(chip)
            }

            if (state.selectedFilter != StateFilter.ALL) {
                addChip(state.selectedFilter.label) { viewModel.setFilter(StateFilter.ALL) }
            }
            if (state.selectedCategory != null) {
                addChip(state.selectedCategory) { viewModel.setCategory(null) }
            }
            if (state.selectedTracker != null) {
                addChip(state.selectedTracker) { viewModel.setTracker(null) }
            }
            if (state.filterUntagged) {
                addChip("Untagged") { viewModel.setFilterUntagged(false) }
            }
            state.selectedTags.forEach { tag -> addChip("#$tag") { viewModel.toggleTag(tag) } }

            val hasChips = filterChipGroup.childCount > 0
            filterScroll.visibility = if (hasChips) View.VISIBLE else View.GONE
        }
    }
}
