package com.example.musicdao.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
import com.example.federated_ml.models.Pegasos
import com.example.musicdao.MusicBaseFragment
import com.example.musicdao.R
import com.example.musicdao.util.Util
import kotlinx.android.synthetic.main.fragment_recommendation.*
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import java.io.File

class RecommendationFragment : MusicBaseFragment(R.layout.fragment_recommendation) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.w("Recommend", "RecommendationFragment view created")

        refreshRecommend.setOnRefreshListener {
            loadingRecommendations.setVisibility(View.VISIBLE)
            loadingRecommendations.text = "Refreshing recommendations..."

            val blocks = getMusicCommunity().database.getBlocksWithType("publish_release")

            val transaction = activity?.supportFragmentManager?.beginTransaction()

            updateRecommendFragment(blocks[blocks.indices.random()], 0)
            updateRecommendFragment(blocks[blocks.indices.random()], 1)

            loadingRecommendations.setVisibility(View.GONE)

            activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }

            refreshRecommend.isRefreshing = false
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
        }
        else {
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
        val model =  getRecommenderCommunity().recommendStore.getLocalModel() as Pegasos
        val data = getRecommenderCommunity().recommendStore.getNewSongs(100)
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
            updateRecommendFragment(blocks[best], 0)
            updateRecommendFragment(blocks[runnerup], 1)
        }
    }
}
