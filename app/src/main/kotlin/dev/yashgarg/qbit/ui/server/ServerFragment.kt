package dev.yashgarg.qbit.ui.server

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.Selection
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.MainActivity
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.ServerFragmentBinding
import dev.yashgarg.qbit.ui.dialogs.AddTorrentDialog
import dev.yashgarg.qbit.ui.dialogs.RemoveTorrentDialog
import dev.yashgarg.qbit.ui.server.adapter.TorrentListAdapter
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.viewBinding
import dev.yashgarg.qbit.validation.LinkValidator
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ServerFragment : Fragment(R.layout.server_fragment) {
    private val binding by viewBinding(ServerFragmentBinding::bind)
    private val viewModel by viewModels<ServerViewModel>()
    private val linkValidator by lazy { LinkValidator() }
    private var selectedItems: Selection<String>? = null

    private var lastSortOption = SortOption.NAME
    private var lastSortDir = SortDirection.ASC
    private var lastSearchQuery = ""
    private var pendingScrollToTop = false
    private var pendingListReset = false

    @Inject lateinit var torrentListAdapter: TorrentListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        activity?.addOnNewIntentListener {
            val bundle = bundleOf(MainActivity.TORRENT_INTENT_KEY to it?.data.toString())
            handleAddIntent(bundle)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHandlers()
        observeFlows()
        setupDialogResultListener()
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
                    torrentListAdapter.notifyDataSetChanged()
                }
                binding.bottomBar.menu.findItem(R.id.delete_selection).isVisible = false
            }

            setFragmentResultListener(AddTorrentDialog.ADD_TORRENT_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val url = bundle.getString(AddTorrentDialog.TORRENT_KEY)
                viewModel.addTorrentUrl(requireNotNull(url))
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

                uris?.forEach { uri ->
                    requireContext().contentResolver.openInputStream(uri).use { stream ->
                        viewModel.addTorrentFile(requireNotNull(stream).readBytes())
                    }
                }
            }
        }
    }

    private fun handleAddIntent(bundle: Bundle?) {
        val uri: String? = (bundle ?: arguments)?.getString(MainActivity.TORRENT_INTENT_KEY)
        (bundle ?: arguments)?.clear()
        if (!uri.isNullOrEmpty()) {
            when {
                linkValidator.isValid(uri) -> viewModel.addTorrentUrl(uri)
                uri.startsWith("content://") || uri.startsWith("file://") -> {
                    requireContext().contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                        viewModel.addTorrentFile(requireNotNull(stream).readBytes())
                    }
                }
            }
        }
    }

    private fun setupHandlers() {
        with(binding) {
            torrentListAdapter.onItemClick = { hash ->
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

            torrentListAdapter.makeSelectable(torrentRv) { selection ->
                selectedItems = selection

                bottomBar.menu.apply {
                    findItem(R.id.delete_selection).apply {
                        isVisible = selection.size() > 0
                        setOnMenuItemClickListener {
                            RemoveTorrentDialog.newInstance()
                                .show(childFragmentManager, RemoveTorrentDialog.TAG)
                            true
                        }
                    }

                    findItem(R.id.pause_selection).apply {
                        isVisible = selection.size() > 0
                        setOnMenuItemClickListener {
                            selectedItems?.toList()?.let { hashes ->
                                viewModel.toggleTorrentsState(true, hashes)
                            }
                            close()
                            true
                        }
                    }

                    findItem(R.id.resume_selection).apply {
                        isVisible = selection.size() > 0
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
                AddTorrentDialog.newInstance().show(childFragmentManager, AddTorrentDialog.TAG)
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
                    R.id.category -> {
                        Toast.makeText(requireContext(), "Not implemented", Toast.LENGTH_SHORT)
                            .show()
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
                        findNavController()
                            .navigate(R.id.action_serverFragment_to_configFragment)
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
                            torrentListAdapter.submitList(emptyList()) {
                                torrentListAdapter.submitList(torrents) {
                                    if (scroll) torrentRv.scrollToPosition(0)
                                }
                            }
                        } else {
                            torrentListAdapter.submitList(torrents) {
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
            if ((sortChanged || searchChanged) && !state.dataLoading && !state.hasError) {
                pendingScrollToTop = true
                pendingListReset = true
            }
            lastSortOption = state.sortOption
            lastSortDir = state.sortDirection
            lastSearchQuery = state.searchQuery

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
}
