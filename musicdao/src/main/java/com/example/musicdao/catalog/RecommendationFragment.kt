package com.example.musicdao.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
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
import kotlinx.android.synthetic.main.fragment_recommendation.*

/**
 * A screen showing an overview of playlists to browse through
 */
class RecommendationFragment : MusicBaseFragment(R.layout.fragment_release_overview) {
    private val store = this.getRecommenderCommunity().store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store.key = getIpv8().myPeer.publicKey.keyToBin()
        Log.w("Recommend", "RecommendationFragment view created")

        lifecycleScope.launchWhenCreated {
            while (isActive && isAdded && !isDetached) {
                if (activity is MusicService && debugText != null) {
                    debugText.text = (activity as MusicService).getStatsOverview()
                }
                refreshRecommendations()
                delay(3000)
            }
        }


//        refreshRecommend.setOnRefreshListener {
//            Log.w("Recommend", "Refreshing recommendation")
//            loadingRecommendations.text = "Refreshing recommendations..."
//            refreshRecommendations()
//            refreshRecommend.isRefreshing = false
//        }
    }

    private fun addCoverFragment(block: TrustChainBlock) {
        Log.w("Recommend", "Adding recommendation coverFragment")
        val coverArt = Util.findCoverArt(File(context?.cacheDir?.path + "/" + Util.sanitizeString(block.transaction["torrentInfoName"] as String)))
        val coverFragment = PlaylistCoverFragment(block, 0, coverArt)
        val transaction = activity?.supportFragmentManager?.beginTransaction()
        transaction?.add(R.id.recommend_layout, coverFragment, "recommendation")
        activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }
    }

    /**
     * List all the releases that are currently loaded in the local trustchain database. If keyword
     * search is enabled (searchQuery variable is set) then it also filters the database
     */
    private fun refreshRecommendations() {
        Log.w("Recommend", "Retrieving local recommendation model")
        val model = store.getLocalModel("Pegasos") as Pegasos
        val data = store.getNewSongs(100)
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
