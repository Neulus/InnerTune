package com.zionhuang.music.ui.fragments.search

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.paging.LoadState.Loading
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialFadeThrough
import com.zionhuang.music.R
import com.zionhuang.music.constants.MediaConstants.EXTRA_LINK_HANDLER
import com.zionhuang.music.constants.MediaConstants.EXTRA_SONG_ID
import com.zionhuang.music.databinding.LayoutRecyclerviewBinding
import com.zionhuang.music.extensions.addOnClickListener
import com.zionhuang.music.ui.activities.MainActivity
import com.zionhuang.music.ui.adapters.LoadStateAdapter
import com.zionhuang.music.ui.adapters.NewPipeSearchResultAdapter
import com.zionhuang.music.ui.fragments.base.BindingFragment
import com.zionhuang.music.ui.listeners.SearchFilterListener
import com.zionhuang.music.viewmodels.PlaybackViewModel
import com.zionhuang.music.viewmodels.SearchViewModel
import com.zionhuang.music.viewmodels.SongsViewModel
import com.zionhuang.music.youtube.newpipe.ExtractorHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeSearchFragment : BindingFragment<LayoutRecyclerviewBinding>() {
    override fun getViewBinding() = LayoutRecyclerviewBinding.inflate(layoutInflater)

    private val args: YouTubeSearchFragmentArgs by navArgs()
    private val query by lazy { args.searchQuery }

    private val viewModel by viewModels<SearchViewModel>()
    private val songsViewModel by activityViewModels<SongsViewModel>()
    private val playbackViewModel by activityViewModels<PlaybackViewModel>()

    private val searchFilterListener = object : SearchFilterListener {
        override var filter: String
            get() = viewModel.filter
            set(value) {
                viewModel.filter = value
                searchResultAdapter.refresh()
            }
    }

    private val searchResultAdapter: NewPipeSearchResultAdapter = NewPipeSearchResultAdapter(searchFilterListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().apply { duration = 300L }
        exitTransition = MaterialFadeThrough().apply { duration = 300L }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        (requireActivity() as MainActivity).supportActionBar?.title = query

        searchResultAdapter.apply {
            streamPopupMenuListener = songsViewModel.streamPopupMenuListener
            addLoadStateListener { loadState ->
                binding.progressBar.isVisible = loadState.refresh is Loading
                binding.btnRetry.isVisible = loadState.refresh is LoadState.Error
                binding.errorMsg.isVisible = loadState.refresh is LoadState.Error
                if (loadState.refresh is LoadState.Error) {
                    binding.errorMsg.text =
                        (loadState.refresh as LoadState.Error).error.localizedMessage
                }
            }
        }
        binding.btnRetry.setOnClickListener { searchResultAdapter.retry() }
        binding.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultAdapter.withLoadStateFooter(LoadStateAdapter { searchResultAdapter.retry() })
            addOnClickListener { pos, view ->
                if (pos == 0) return@addOnClickListener
                when (val item = searchResultAdapter.getItemByPosition(pos)!!) {
                    is StreamInfoItem -> {
                        playbackViewModel.playFromSearch(
                            requireActivity(), query, bundleOf(
                                EXTRA_SONG_ID to ExtractorHelper.extractVideoId(item.url),
                                EXTRA_LINK_HANDLER to ExtractorHelper.getSearchQueryHandler(query, listOf(searchFilterListener.filter))
                            )
                        )
                    }
                    is PlaylistInfoItem -> {
                        exitTransition = MaterialElevationScale(false).apply { duration = 300L }
                        reenterTransition = MaterialElevationScale(true).apply { duration = 300L }
                        val transitionName = getString(R.string.youtube_playlist_transition_name)
                        val extras = FragmentNavigatorExtras(view to transitionName)
                        val directions = YouTubeSearchFragmentDirections.actionSearchResultFragmentToYouTubePlaylistFragment(item.url)
                        findNavController().navigate(directions, extras)
                    }
                    is ChannelInfoItem -> {
                        exitTransition = MaterialElevationScale(false).apply { duration = 300L }
                        reenterTransition = MaterialElevationScale(true).apply { duration = 300L }
                        val transitionName = getString(R.string.youtube_channel_transition_name)
                        val extras = FragmentNavigatorExtras(view to transitionName)
                        val directions = YouTubeSearchFragmentDirections.actionSearchResultFragmentToYouTubeChannelFragment(item.url)
                        findNavController().navigate(directions, extras)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.search(query).collectLatest {
                searchResultAdapter.submitData(it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.search_icon, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            NavHostFragment.findNavController(this).navigate(R.id.action_searchResultFragment_to_searchSuggestionFragment)
        }
        return true
    }

    companion object {
        private const val TAG = "SearchResultFragment"
    }
}