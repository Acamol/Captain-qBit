package dev.yashgarg.qbit.ui.torrent.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_HIGH
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_MAXIMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_NORMAL
import dev.yashgarg.qbit.ui.compose.FILE_PRIORITY_SKIP
import dev.yashgarg.qbit.ui.compose.TorrentContentTreeView
import dev.yashgarg.qbit.ui.compose.isSkipped
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsViewModel
import dev.yashgarg.qbit.utils.ClipboardUtil

class TorrentFilesFragment : Fragment() {
    private val viewModel by
        viewModels<TorrentDetailsViewModel>(ownerProducer = { requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).apply {
            // Dispose the composition when this fragment's view lifecycle is destroyed so it
            // unregisters its snapshot apply-observer. Binding to viewLifecycleOwner explicitly
            // is required here: inside a ViewPager2 the view-tree strategy resolves to a
            // longer-lived owner and the ComposeView leaks.
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            setContent {
                val state by viewModel.uiState.collectAsState()
                val scrollState = rememberNestedScrollInteropConnection()

                Mdc3Theme(setTextColors = true, setDefaultFontFamily = true) {
                    FilesListView(
                        state,
                        Modifier.nestedScroll(scrollState),
                        onNodeLongClick = { item ->
                            showNodeMenu(item, state.torrentProperties?.savePath)
                        },
                        // Checkbox (and file tap) = download on/off; finer priorities are in the
                        // long-press menu.
                        onToggleDownload = { item ->
                            viewModel.setFilePriority(
                                fileIndices(item),
                                if (item.isSkipped()) FILE_PRIORITY_NORMAL else FILE_PRIORITY_SKIP,
                            )
                        },
                    )
                }
            }
        }
    }

    // Long-press menu: works for files and folders alike, so folders get a way to set the
    // priority of everything beneath them (their tap is taken by expand/collapse).
    private fun showNodeMenu(item: ContentTreeItem, savePath: String?) {
        val options =
            arrayOf(
                getString(CommonR.string.file_menu_priority),
                getString(CommonR.string.file_menu_path),
            )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPriorityDialog(item)
                    1 -> showPathDialog(item, savePath)
                }
            }
            .show()
    }

    private fun showPriorityDialog(item: ContentTreeItem) {
        val labels =
            arrayOf(
                getString(CommonR.string.file_priority_skip),
                getString(CommonR.string.file_priority_normal),
                getString(CommonR.string.file_priority_high),
                getString(CommonR.string.file_priority_maximal),
            )
        val priorities =
            intArrayOf(
                FILE_PRIORITY_SKIP,
                FILE_PRIORITY_NORMAL,
                FILE_PRIORITY_HIGH,
                FILE_PRIORITY_MAXIMAL,
            )
        // Preselect the file's priority; for a folder, only when its files all agree.
        val checked = priorities.indexOf(commonPriority(item) ?: -1)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(CommonR.string.file_priority_title))
            .setNegativeButton(getString(CommonR.string.cancel), null)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setFilePriority(fileIndices(item), priorities[which])
                dialog.dismiss()
            }
            .show()
    }

    /** All torrent file indices under this node (itself, for a file). */
    private fun fileIndices(item: ContentTreeItem): List<Int> =
        item.item?.let { listOf(it.index) } ?: item.children.orEmpty().flatMap { fileIndices(it) }

    /** The single priority shared by every file under this node, or null when they differ. */
    private fun commonPriority(item: ContentTreeItem): Int? {
        val priorities = collectPriorities(item).distinct()
        return priorities.singleOrNull()
    }

    private fun collectPriorities(item: ContentTreeItem): List<Int> =
        item.item?.let { listOf(it.priority) }
            ?: item.children.orEmpty().flatMap { collectPriorities(it) }

    private fun showPathDialog(item: ContentTreeItem, savePath: String?) {
        val fullPath = savePath?.let { joinPath(it, item.path) } ?: item.path
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (item.item == null) "Folder path" else "File path")
            .setMessage(fullPath)
            .setPositiveButton("Copy") { _, _ ->
                ClipboardUtil.copyToClipboard(requireContext(), "content-path", fullPath)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // qBittorrent uses "/" inside torrent paths; match the save path's own separator.
    private fun joinPath(base: String, relative: String): String {
        val separator = if (base.contains("\\") && !base.contains("/")) "\\" else "/"
        val normalizedRelative = if (separator == "\\") relative.replace("/", "\\") else relative
        return base.trimEnd('/', '\\') + separator + normalizedRelative
    }
}

@Composable
fun FilesListView(
    state: TorrentDetailsState,
    modifier: Modifier = Modifier,
    onNodeLongClick: (ContentTreeItem) -> Unit = {},
    onToggleDownload: ((ContentTreeItem) -> Unit)? = null,
) {
    if (state.contentLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.contentTree.isEmpty()) {
        Center(modifier) { Text("No content found") }
    } else {
        TorrentContentTreeView(modifier, state.contentTree, onNodeLongClick, onToggleDownload)
    }
}
