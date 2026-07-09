package dev.yashgarg.qbit.ui.server

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.Selection
import com.google.android.material.chip.Chip
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.ServerFragmentBinding
import dev.yashgarg.qbit.ui.dialogs.AddTorrentDialog
import dev.yashgarg.qbit.ui.dialogs.RemoveTorrentDialog
import dev.yashgarg.qbit.ui.server.adapter.TorrentListAdapter
import dev.yashgarg.qbit.utils.TorrentHashUtil
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.validation.LinkValidator
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ServerFragment : Fragment(R.layout.server_fragment) {
    private val binding by viewBinding(ServerFragmentBinding::bind)
    private val viewModel by viewModels<ServerViewModel>()
    private val linkValidator by lazy { LinkValidator() }
    private var selectedItems: Selection<String>? = null
    private var clearSelectionCallback: OnBackPressedCallback? = null
    private var searchBackCallback: OnBackPressedCallback? = null
    private var drawerBackCallback: OnBackPressedCallback? = null

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

    // View-scoped for the same reason: both hold the current binding/cached drawer-render state.
    private var drawerController: ServerDrawerController? = null
    private var actionDialogs: ServerActionDialogs? = null

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
        actionDialogs = ServerActionDialogs(this, viewModel)
        drawerController =
            ServerDrawerController(
                fragment = this,
                binding = binding,
                viewModel = viewModel,
                onCategoryLongPress = { name -> actionDialogs?.showCategoryLongPressDialog(name) },
                onTagLongPress = { name -> actionDialogs?.showTagLongPressDialog(name) },
            )
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
        searchBackCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    closeSearch()
                }
            }
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                requireNotNull(searchBackCallback),
            )
        drawerBackCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                }
            }
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                requireNotNull(drawerBackCallback),
            )
        binding.drawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    drawerBackCallback?.isEnabled = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    drawerBackCallback?.isEnabled = false
                }
            }
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
        lastFilterUntagged = false
        torrentListAdapter = null
        drawerController = null
        actionDialogs = null
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

    private var lastFilterUntagged = false

    private fun openSearch() {
        with(binding) {
            searchLayout.visibility = View.VISIBLE
            searchEt.requestFocus()
            val imm = requireContext().getSystemService<InputMethodManager>()
            imm?.showSoftInput(searchEt, InputMethodManager.SHOW_IMPLICIT)
        }
        searchBackCallback?.isEnabled = true
    }

    private fun closeSearch() {
        with(binding) {
            searchEt.setText("")
            viewModel.setSearchQuery("")
            searchLayout.visibility = View.GONE
            val imm = requireContext().getSystemService<InputMethodManager>()
            imm?.hideSoftInputFromWindow(searchEt.windowToken, 0)
        }
        searchBackCallback?.isEnabled = false
    }

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
                            selectedItems?.toList()?.let { hashes ->
                                actionDialogs?.showBulkCategoryPicker(hashes)
                            }
                            true
                        }
                    }

                    findItem(R.id.tags_selection).apply {
                        isVisible = hasSelection
                        setOnMenuItemClickListener {
                            selectedItems?.toList()?.let { hashes ->
                                actionDialogs?.showBulkTagsPicker(hashes)
                            }
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
                        if (searchLayout.visibility == View.VISIBLE) closeSearch() else openSearch()
                        true
                    }
                    R.id.sort_list -> {
                        actionDialogs?.showSortPicker()
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
                        actionDialogs?.showManageTagsDialog()
                        true
                    }
                    R.id.manage_categories -> {
                        actionDialogs?.showManageCategoriesDialog()
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
        viewModel.uiState.collectWithLifecycle(this, ::render)

        viewModel.sortedTorrents.collectWithLifecycle(this) { torrents ->
            if (torrents == null) return@collectWithLifecycle
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

        viewModel.status.collectWithLifecycle(this) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }

        viewModel.intent.collectWithLifecycle(this) { handleAddIntent(null) }
    }

    private fun render(state: ServerScreenState) {
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

            drawerController?.update(state)
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

    private fun updateFilterChips(state: ServerScreenState) {
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
