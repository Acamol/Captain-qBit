package dev.yashgarg.qbit.ui.server

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import dev.yashgarg.qbit.utils.friendlyMessage
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.validation.LinkValidator
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
        binding.drawerStatsButton.setOnClickListener { actionDialogs?.showStatisticsDialog() }
        binding.drawerServerSwitcher.setOnClickListener { actionDialogs?.showServerPicker() }
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
        }
    }

    private fun handleAddIntent(bundle: Bundle?) {
        val uri: String? = (bundle ?: arguments)?.getString(MainActivity.TORRENT_INTENT_KEY)
        (bundle ?: arguments)?.clear()
        if (uri.isNullOrEmpty()) return
        // onNewIntent can deliver while our view is destroyed (app in background). Accessing
        // viewLifecycleOwner would crash, so stash the uri and let the viewModel.intent pass
        // consume it from arguments once the view is recreated.
        if (view == null) {
            arguments = bundleOf(MainActivity.TORRENT_INTENT_KEY to uri)
            return
        }
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

            // Pad the list by the bottom bar's height so the last torrent can clear the bar when
            // the list fills the screen but isn't long enough to scroll (paired with the RV's
            // clipToPadding=false). The bar overlaps the list in the CoordinatorLayout.
            bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                val barHeight = bottom - top
                if (torrentRv.paddingBottom != barHeight) {
                    torrentRv.setPadding(
                        torrentRv.paddingLeft,
                        torrentRv.paddingTop,
                        torrentRv.paddingRight,
                        barHeight,
                    )
                }
            }

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
                    else -> false
                }
            }

            // Secondary actions moved off the bottom bar into the drawer footer.
            drawerSpeedLimitSwitch.setOnClickListener { viewModel.toggleSpeedLimits() }
            drawerManageCategories.setOnClickListener {
                drawerLayout.closeDrawer(Gravity.START)
                actionDialogs?.showManageCategoriesDialog()
            }
            drawerManageTags.setOnClickListener {
                drawerLayout.closeDrawer(Gravity.START)
                actionDialogs?.showManageTagsDialog()
            }
            drawerSettings.setOnClickListener {
                findNavController().navigate(R.id.action_serverFragment_to_settingsFragment)
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
                                ensureBottomBarVisibleIfNotScrollable()
                            }
                        }
                    } else {
                        torrentListAdapter?.submitList(torrents) {
                            if (scroll) torrentRv.scrollToPosition(0)
                            ensureBottomBarVisibleIfNotScrollable()
                        }
                    }
                }
            }
        }

        viewModel.status.collectWithLifecycle(this) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }

        viewModel.categoryColors.collectWithLifecycle(this) {
            torrentListAdapter?.categoryColors = it
        }

        viewModel.intent.collectWithLifecycle(this) { handleAddIntent(null) }

        // Consume a URI stashed while our view was destroyed (e.g. a torrent opened from another
        // app
        // while on the info screen) right away, rather than waiting for the next sync tick.
        handleAddIntent(null)
    }

    // With hideOnScroll, a hidden bottom bar can only be revealed by scrolling up. If the list
    // isn't tall enough to scroll, the bar (and its action buttons) would be stuck hidden - so
    // force it back into view whenever the list can't scroll in either direction.
    private fun ensureBottomBarVisibleIfNotScrollable() {
        val rv = binding.torrentRv
        rv.post {
            if (!rv.canScrollVertically(-1) && !rv.canScrollVertically(1)) {
                binding.bottomBar.performShow()
            }
        }
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
            drawerServerName.text = state.serverName ?: "Servers"

            // Reflect current alt-speed-limits state on the drawer toggle. Safe to set directly:
            // the row uses a click listener (user-initiated), so this won't re-trigger a toggle.
            drawerSpeedLimitSwitch.isChecked = state.speedLimitMode != 0

            if (state.hasError) {
                listLoader.visibility = View.GONE
                speedTv.visibility = View.GONE
                val errorFallback =
                    requireContext().getString(dev.yashgarg.qbit.common.R.string.error)
                errorTv.text = state.error?.friendlyMessage(errorFallback) ?: errorFallback
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
                        "↓ ${serverState.dlInfoSpeed.toHumanReadable()}/s  ↑ ${serverState.upInfoSpeed.toHumanReadable()}/s   ${serverState.freeSpace.toHumanReadable()} free"
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
