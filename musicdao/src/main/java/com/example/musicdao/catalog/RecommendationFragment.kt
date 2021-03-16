package com.example.musicdao.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.federated_ml.models.Pegasos
import com.example.musicdao.MusicBaseFragment
import kotlinx.coroutines.delay
import com.example.musicdao.R
import com.example.musicdao.util.Util
import kotlinx.android.synthetic.main.fragment_recommendation.*
import kotlinx.android.synthetic.main.fragment_release.*
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import java.io.File

class RecommendationFragment : MusicBaseFragment(R.layout.fragment_recommendation) {
    private var isActive = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.w("Recommend", "RecommendationFragment view created")

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                if (loadingRecommendations != null) {
                    loadingRecommendations.setVisibility(View.VISIBLE)
                    loadingRecommendations.text = "Refreshing recommendations..."

                    refreshRecommendations()

                    val transaction = activity?.supportFragmentManager?.beginTransaction()
                    loadingRecommendations.setVisibility(View.GONE)
                    activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }

                    refreshRecommend.isRefreshing = false
                }
                delay(10000)
                Log.w("Recommend", "Refreshing...")
            }
        }

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                val community = getRecommenderCommunity()
                val localModel = community.recommendStore.getLocalModel() as Pegasos
                community.performRemoteModelExchange(localModel)
                delay(30000)
            }
        }
    }

    private fun updateRecommendFragment(block: TrustChainBlock, recNum: Int) {
        Log.w("Recommend", "Adding recommendation coverFragment")
        val transaction = activity?.supportFragmentManager?.beginTransaction()

        val recFrag = activity?.supportFragmentManager?.findFragmentByTag("recommendation$recNum")
        if (recFrag != null) transaction?.remove(recFrag)

        val torrentName = block.transaction["torrentInfoName"]
        val path = if (torrentName != null) {
            context?.cacheDir?.path + "/" + Util.sanitizeString(torrentName as String)
        } else {
            context?.cacheDir?.path + "/" + "notfound.jpg"
        }
        val coverArt = Util.findCoverArt(File(path))
        val coverFragment = PlaylistCoverFragment(block, 0, coverArt)
        transaction?.add(R.id.recommend_layout, coverFragment, "recommendation$recNum")

        activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }
    }

    /**
     * List all the releases that are currently loaded in the local trustchain database. If keyword
     * search is enabled (searchQuery variable is set) then it also filters the database
     */
    private fun refreshRecommendations() {
        Log.w("Recommend", "Retrieving local recommendation model")
        val data = getRecommenderCommunity().recommendStore.getNewSongs(50)
        val songFeatures = data.first
        val blocks = data.second
        val model = getRecommenderCommunity().recommendStore.getLocalModel() as Pegasos
        val predictions = model.predict(songFeatures)
        var best = 0
        var runnerup = 1
        for ((i, pred) in predictions.withIndex()) {
            if (pred > predictions[best]) {
                runnerup = best
                best = i
            }
        }
        val debugScore = predictions[best].toString()
        Log.w("Recommender", "After refreshing, best local score is $debugScore")
        updateRecommendFragment(blocks[best], 0)
        updateRecommendFragment(blocks[runnerup], 1)
    }
}
