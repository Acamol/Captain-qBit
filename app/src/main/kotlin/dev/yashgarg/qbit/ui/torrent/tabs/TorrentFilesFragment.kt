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
import dev.yashgarg.qbit.data.models.ContentTreeItem
import dev.yashgarg.qbit.ui.compose.Center
import dev.yashgarg.qbit.ui.compose.CenterLinearLoading
import dev.yashgarg.qbit.ui.compose.TorrentContentTreeView
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsViewModel
import dev.yashgarg.qbit.utils.ClipboardUtil

class TorrentFilesFragment : Fragment() {
    private val viewModel by
        viewModels<TorrentDetailsViewModel>(ownerProducer = { requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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
                            showPathDialog(item, state.torrentProperties?.savePath)
                        },
                    )
                }
            }
        }
    }

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
) {
    if (state.contentLoading) {
        CenterLinearLoading(modifier, R.color.md_theme_dark_seed)
    } else if (state.contentTree.isEmpty()) {
        Center(modifier) { Text("No content found") }
    } else {
        TorrentContentTreeView(modifier, state.contentTree, onNodeLongClick)
    }
}
