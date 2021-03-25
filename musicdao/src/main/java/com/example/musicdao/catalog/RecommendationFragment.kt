package com.example.musicdao.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
import com.example.federated_ml.models.feature_based.Pegasos
import com.example.musicdao.MusicBaseFragment
import com.example.musicdao.R
import com.example.musicdao.util.Util
import kotlinx.android.synthetic.main.fragment_recommendation.*
import kotlinx.coroutines.delay
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import java.io.File
import java.lang.Double.NEGATIVE_INFINITY

class RecommendationFragment : MusicBaseFragment(R.layout.fragment_recommendation) {
    private var isActive = true
    private val test = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.w("Recommend", "RecommendationFragment view created")

        lifecycleScope.launchWhenCreated {
            while (isActive) {
                getRecommenderCommunity().initiateWalkingModel()
                delay(10 * 1000)
            }
        }
        refreshRecommend.setOnRefreshListener {
            if ( getRecommenderCommunity().recommendStore.globalSongCount() >  0 ) {
                loadingRecommendations.setVisibility(View.VISIBLE)
                loadingRecommendations.text = "Refreshing recommendations..."
                refreshRecommendations()
                val transaction = activity?.supportFragmentManager?.beginTransaction()
                loadingRecommendations.setVisibility(View.GONE)
                activity?.runOnUiThread { transaction?.commitAllowingStateLoss() }
                refreshRecommend.isRefreshing = false
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

    private fun getBaggedPredictions(data : Pair<Array<Array<Double>>, List<TrustChainBlock>>) : Array<Double> {
        var jointRelease = Array(data.second.size) { _ -> 0.0}
        val modelNames = arrayOf("Pegasos")
        for (name in modelNames) {
            Log.w("Recommend", "Getting model $name")
            var colab = getRecommenderCommunity().recommendStore.getLocalModel(name)
            var bestRelease = (colab as Pegasos).predict(data.first).toTypedArray()
            val sum = bestRelease.sumByDouble { it }
            bestRelease = bestRelease.indices.map { bestRelease[it] / sum }.toTypedArray()
            jointRelease = jointRelease.indices.map { jointRelease[it] + bestRelease[it] }.toTypedArray()
        }
        return jointRelease
    }

    /**
     * List all the releases that are currently loaded in the local trustchain database. If keyword
     * search is enabled (searchQuery variable is set) then it also filters the database
     */
    private fun refreshRecommendations() {
        val recStore = getRecommenderCommunity().recommendStore
        val data = recStore.getNewSongs()
        val blocks = data.second

        var colab = recStore.getLocalModel("MatrixFactorization") as MatrixFactorization
        if (colab.numSongs == 0) {
            colab = MatrixFactorization(recStore.globalSongCount(), recStore.getSongIds(), recStore.getPlaycounts())
            recStore.storeModelLocally(colab)
        }
        val bestRelease = colab.predict()
        var bestBlock = blocks[0]
        for (block in blocks) {
            bestBlock = block
            if ("${block.transaction["title"]}-${block.transaction["artist"]}" == bestRelease)
                break
        }
        updateRecommendFragment(bestBlock, 0)

        Log.w("Recommend", "Retrieving local recommendation model")
        val predictions = getBaggedPredictions(data)
        var best = 0
        var bestScore = NEGATIVE_INFINITY
        for ((i, pred) in predictions.withIndex()) {
            if (bestScore < pred) {
                best = i
                bestScore = pred
            }
        }
        Log.w("Recommend", "PICKED BLOCK $best with score $bestScore")
        val debugScore = predictions[best].toString()
        Log.w("Recommender", "After refreshing, best local score is $debugScore")
        updateRecommendFragment(blocks[best], 1)
    }
}
