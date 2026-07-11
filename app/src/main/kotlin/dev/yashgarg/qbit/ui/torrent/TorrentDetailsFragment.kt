package dev.yashgarg.qbit.ui.torrent

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialElevationScale
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.TorrentDetailsFragmentBinding
import dev.yashgarg.qbit.ui.dialogs.RemoveTorrentDialog
import dev.yashgarg.qbit.ui.dialogs.RenameTorrentDialog
import dev.yashgarg.qbit.ui.torrent.adapter.TorrentDetailsAdapter
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import me.saket.cascade.CascadePopupMenu
import me.saket.cascade.MenuItemViewHolder
import qbittorrent.models.Torrent

@AndroidEntryPoint
class TorrentDetailsFragment : Fragment(R.layout.torrent_details_fragment) {
    private val binding by viewBinding(TorrentDetailsFragmentBinding::bind)
    private val viewModel by viewModels<TorrentDetailsViewModel>()

    private lateinit var torrentInfoAdapter: TorrentDetailsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialElevationScale(false)
        reenterTransition = MaterialElevationScale(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { it.findNavController().navigateUp() }
        torrentInfoAdapter = TorrentDetailsAdapter(this)
        binding.pager.adapter = torrentInfoAdapter

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
                tab.text =
                    when (position) {
                        0 -> "Info"
                        1 -> "Files"
                        2 -> "Trackers"
                        else -> "Peers"
                    }
            }
            .attach()
        observeFlows()
    }

    private fun setupMenu(torrent: Torrent) {
        setupDialogListeners(torrent)
        binding.toolbar.findViewById<View>(R.id.overflow_menu).setOnClickListener { view ->
            val density = resources.displayMetrics.density
            val desiredWidth = (280 * density).toInt()
            val maxWidth = resources.displayMetrics.widthPixels - (32 * density).toInt()
            val popupMenu =
                CascadePopupMenu(
                    requireContext(),
                    view,
                    styler = CascadePopupMenu.Styler(menuItem = ::showFullTitleOnLongPress),
                    fixedWidth = minOf(desiredWidth, maxWidth),
                )
            popupMenu.menu.apply {
                add("Pause").setIcon(R.drawable.twotone_pause_24).setOnMenuItemClickListener {
                    viewModel.toggleTorrent(true, torrent.hash)
                    true
                }
                add("Resume").setIcon(R.drawable.twotone_play_arrow_24).setOnMenuItemClickListener {
                    viewModel.toggleTorrent(false, torrent.hash)
                    true
                }
                add("Delete").setIcon(R.drawable.twotone_delete_24).setOnMenuItemClickListener {
                    RemoveTorrentDialog.newInstance()
                        .show(childFragmentManager, RemoveTorrentDialog.TAG)
                    true
                }
                addSubMenu("Copy").also {
                    it.setIcon(R.drawable.twotone_content_copy_24)
                    it.add("Name").setOnMenuItemClickListener {
                        ClipboardUtil.copyToClipboard(
                            requireContext(),
                            "torrent-name",
                            torrent.name
                        )
                        true
                    }
                    it.add("Info hash").setOnMenuItemClickListener {
                        ClipboardUtil.copyToClipboard(
                            requireContext(),
                            "torrent-hash",
                            torrent.hash
                        )
                        true
                    }
                    it.add("Magnet link").setOnMenuItemClickListener {
                        ClipboardUtil.copyToClipboard(
                            requireContext(),
                            "torrent-magnet",
                            torrent.magnetUri
                        )
                        true
                    }
                }
                add("Force recheck")
                    .setIcon(R.drawable.twotone_find_in_page_24)
                    .setOnMenuItemClickListener {
                        viewModel.forceRecheck(torrent.hash)
                        true
                    }
                add("Force reannounce")
                    .setIcon(R.drawable.twotone_restore_page_24)
                    .setOnMenuItemClickListener {
                        viewModel.forceReannounce(torrent.hash)
                        true
                    }
                add("Rename")
                    .setIcon(R.drawable.twotone_drive_file_rename_outline_24)
                    .setOnMenuItemClickListener {
                        val dialog = RenameTorrentDialog.newInstance()
                        dialog.arguments =
                            bundleOf(RenameTorrentDialog.TORRENT_NAME_KEY to torrent.name)
                        dialog.show(childFragmentManager, RenameTorrentDialog.TAG)
                        true
                    }
                add("Set category")
                    .setIcon(R.drawable.outline_category_24)
                    .setOnMenuItemClickListener {
                        showCategoryPicker(torrent)
                        true
                    }
                add("Set tags")
                    .setIcon(R.drawable.outline_filter_list_24)
                    .setOnMenuItemClickListener {
                        showTagsPicker(torrent)
                        true
                    }
                add("Automatic Torrent Management")
                    .setIcon(R.drawable.twotone_sync_24)
                    .setCheckable(true)
                    .setChecked(torrent.autoTmm)
                    .setOnMenuItemClickListener {
                        viewModel.setAutoManagement(!torrent.autoTmm)
                        true
                    }
                add("Set save path…")
                    .setIcon(R.drawable.twotone_folder_24)
                    .setOnMenuItemClickListener {
                        showSavePathDialog(torrent)
                        true
                    }
            }
            popupMenu.show()
        }
    }

    // Menu rows use a fixed width and ellipsize long labels. Let a long-press reveal
    // the full text via a toast, but only when the label is actually truncated.
    private fun showFullTitleOnLongPress(holder: MenuItemViewHolder) {
        holder.itemView.setOnLongClickListener {
            val layout = holder.titleView.layout
            val truncated =
                layout != null && (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
            if (truncated) {
                Toast.makeText(requireContext(), holder.titleView.text, Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }
    }

    private fun setupDialogListeners(torrent: Torrent) {
        childFragmentManager.apply {
            setFragmentResultListener(RemoveTorrentDialog.REMOVE_TORRENT_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val deleteFiles = bundle.getBoolean(RemoveTorrentDialog.TORRENT_KEY)
                // Navigation + the success toast happen only once the removal actually
                // succeeds (see viewModel.removed / viewModel.status in observeFlows). On
                // failure the status flow surfaces the real error and we stay on screen.
                viewModel.removeTorrent(torrent.hash, deleteFiles)
            }

            setFragmentResultListener(RenameTorrentDialog.RENAME_TORRENT_KEY, viewLifecycleOwner) {
                _,
                bundle ->
                val torrentName = bundle.getString(RenameTorrentDialog.RENAME_KEY)
                viewModel.renameTorrent(requireNotNull(torrentName), torrent.hash)
            }
        }
    }

    private fun showCategoryPicker(torrent: Torrent) {
        val state = viewModel.uiState.value
        val options = listOf("") + state.availableCategories
        val labels = options.map { it.ifBlank { "None" } }.toTypedArray()
        val checked = options.indexOf(torrent.category).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set category")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setCategory(options[which])
                dialog.dismiss()
            }
            .setNeutralButton("New…") { _, _ -> showCreateCategoryDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateCategoryDialog() {
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

    private fun showTagsPicker(torrent: Torrent) {
        val state = viewModel.uiState.value
        val tags = state.availableTags
        if (tags.isEmpty()) {
            showCreateTagDialog()
            return
        }
        val initialChecked = BooleanArray(tags.size) { torrent.tags.contains(tags[it]) }
        val currentChecked = initialChecked.copyOf()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage tags")
            .setMultiChoiceItems(tags.toTypedArray(), currentChecked) { _, which, isChecked ->
                currentChecked[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val toAdd = tags.filterIndexed { i, _ -> currentChecked[i] && !initialChecked[i] }
                val toRemove =
                    tags.filterIndexed { i, _ -> !currentChecked[i] && initialChecked[i] }
                if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                    viewModel.setTags(toAdd, toRemove)
                }
            }
            .setNeutralButton("New tag…") { _, _ -> showCreateTagDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateTagDialog() {
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
                        viewModel.setTags(listOf(name), emptyList())
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    private fun showSavePathDialog(torrent: Torrent) {
        val view = layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Save path"
        tiet?.setText(torrent.savePath)
        tiet?.setSelection(tiet.text?.length ?: 0)

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set save path")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val path = tiet?.text?.toString()?.trim()
                if (!path.isNullOrBlank()) {
                    viewModel.setSavePath(path)
                    dialog.dismiss()
                } else {
                    til?.error = "Path cannot be empty"
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    private fun observeFlows() {
        viewModel.uiState.collectWithLifecycle(this, ::render)

        viewModel.status.collectWithLifecycle(this) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }

        viewModel.removed.collectWithLifecycle(this) { findNavController().navigateUp() }
    }

    private fun render(state: TorrentDetailsState) {
        with(binding) {
            errorBanner.apply {
                text = state.errorReason
                visibility = if (state.errorReason != null) View.VISIBLE else View.GONE
            }
            if (!state.loading && state.error == null) {
                val torrent = requireNotNull(state.torrent)
                torrent.name.apply {
                    toolbar.title = this
                    collapsingToolbar.title = this
                }
                setupMenu(torrent)
            }
        }
    }
}
