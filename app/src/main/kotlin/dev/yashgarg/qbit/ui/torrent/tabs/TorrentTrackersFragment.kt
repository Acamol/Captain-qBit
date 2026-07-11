package dev.yashgarg.qbit.ui.torrent.tabs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.TorrentTrackersFragmentBinding
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsState
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsViewModel
import dev.yashgarg.qbit.ui.torrent.adapter.TorrentTrackersAdapter
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class TorrentTrackersFragment : Fragment(R.layout.torrent_trackers_fragment) {
    private val binding by viewBinding(TorrentTrackersFragmentBinding::bind)
    private val viewModel by
        viewModels<TorrentDetailsViewModel>(ownerProducer = { requireParentFragment() })

    @Inject lateinit var torrentTrackersAdapter: TorrentTrackersAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.trackersRv.adapter = torrentTrackersAdapter
        observeFlows()
    }

    private fun observeFlows() {
        viewModel.uiState.collectWithLifecycle(this, ::render)
    }

    private fun render(state: TorrentDetailsState) {
        if (!state.loading) {
            torrentTrackersAdapter.submitList(state.trackers)
        }
    }
}
