package com.example.musicdao.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.musicdao.MusicBaseFragment
import com.example.musicdao.MusicService
import com.example.musicdao.R
import com.example.musicdao.util.Util
import kotlinx.android.synthetic.main.fragment_release_overview.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import java.io.File

import com.example.federated_ml.models.Pegasos
import com.frostwire.jlibtorrent.Sha1Hash
import kotlinx.android.synthetic.main.fragment_recommendation.*

/**
 * A screen showing an overview of playlists to browse through
 */
class RecommendationFragment : MusicBaseFragment(R.layout.fragment_release_overview) {
    private var releaseRefreshCount = 0
    private var searchQuery = ""
    private val maxPlaylists = 2 // Max playlists to show

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        store.key = getIpv8().myPeer.publicKey.keyToBin()
//        Log.w("Recommend", "RecommendationFragment view created")
//
//        lifecycleScope.launchWhenCreated {
//            while (isActive && isAdded && !isDetached) {
//                if (activity is MusicService && debugText != null) {
//                    debugText.text = (activity as MusicService).getStatsOverview()
//                }
//                refreshRecommendations()
//                delay(3000)
//            }
//        }
//
//
//
//
////        refreshRecommend.setOnRefreshListener {
////            Log.w("Recommend", "Refreshing recommendation")
////            loadingRecommendations.text = "Refreshing recommendations..."
////            refreshRecommendations()
////            refreshRecommend.isRefreshing = false
////        }
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchQuery = arguments?.getString("filter", "") ?: ""
        if (searchQuery != "") {
            getMusicCommunity().performRemoteKeywordSearch(searchQuery)
            activity?.title = "Search results"
            setMenuVisibility(false)
            setHasOptionsMenu(false)
        } else {
            setHasOptionsMenu(true)
        }

        releaseRefreshCount = 0

        lifecycleScope.launchWhenCreated {
            while (isActive && isAdded && !isDetached) {
                if (activity is MusicService && debugText != null) {
                    debugText.text = (activity as MusicService).getStatsOverview()
                }
                if (releaseRefreshCount < 3) {
                    refreshRecommendations()
                }
                delay(10000)
            }
        }
    }

    private fun addCoverFragment(block: TrustChainBlock) {
        val dummyConnectivity = 100
        Log.w("Recommend", "Adding recommendation coverFragment")
//        val coverArt = Util.findCoverArt(File(context?.cacheDir?.path + "/" + Util.sanitizeString(block.transaction["torrentInfoName"] as String)))
//        val coverFragment = PlaylistCoverFragment(block, 0, coverArt)
//        val transaction = activity?.supportFragmentManager?.beginTransaction()
//        transaction?.add(R.id.recommend_layout, coverFragment, "recommendation")
//        activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }
        val magnet = block.transaction["magnet"]
        val title = block.transaction["title"]
        val torrentInfoName = block.transaction["torrentInfoName"]
        val publisher = block.transaction["publisher"]
        if (magnet is String && magnet.length > 0 && title is String && title.length > 0 &&
            torrentInfoName is String && torrentInfoName.length > 0 && publisher is String &&
            publisher.length > 0
        ) {
            val coverArt = Util.findCoverArt(
                File(
                    context?.cacheDir?.path + "/" + Util.sanitizeString(torrentInfoName)
                )
            )
            val transaction = activity?.supportFragmentManager?.beginTransaction()
            val coverFragment = PlaylistCoverFragment(block, dummyConnectivity, coverArt)
            if (coverFragment.filter(searchQuery)) {
                transaction?.add(R.id.release_overview_layout, coverFragment, "releaseCover")
                if (loadingReleases?.visibility == View.VISIBLE) {
                    loadingReleases.visibility = View.GONE
                }
            }
            activity?.runOnUiThread {
                transaction?.commitAllowingStateLoss()
            }
        }
    }

    /**
     * List all the releases that are currently loaded in the local trustchain database. If keyword
     * search is enabled (searchQuery variable is set) then it also filters the database
     */
    private fun refreshRecommendations() {
        Log.w("Recommend", "Retrieving local recommendation model")
        val model =  getRecommenderCommunity().store.getLocalModel("Pegasos") as Pegasos
        val data = getRecommenderCommunity().store.getNewSongs(100)
        if (data != null) {
            val songFeatures = data.first
            val blocks = data.second
            val predictions = model.predict(songFeatures)
            var best = 0
            var runnerup = 0
            for ((i, pred) in predictions.withIndex()) {
                if (pred > predictions[best]) {
                    runnerup = best
                    best = i
                }
            }
            addCoverFragment(blocks[best])
            addCoverFragment(blocks[runnerup])
        }
    }
}
