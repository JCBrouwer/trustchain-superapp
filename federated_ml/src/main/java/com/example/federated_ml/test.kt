package com.example.federated_ml

import com.example.federated_ml.feature_engineering.Preprocessing
import com.example.federated_ml.models.OnlineModel
import smile.classification.DiscreteNaiveBayes
import kotlin.random.Random
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

fun main(){
    println("Running...")
    val totalAmountSongs = 10
    val weakLearners = mutableListOf<WeakLearner>()

    // Create 5 weak learners
    for(i in 1..5) {
        // generate random songs history
        val amountSongs = nextInt(totalAmountSongs)
        val songsHistory = Array<Int>(amountSongs){ nextInt(totalAmountSongs) }

        // initiate user / learner with this songs history
        val wl = WeakLearner(i, songsHistory)
        weakLearners.add(wl)
    }

    val amountFeatures = 10
    val testFeatures = arrayOf(Array<Double>(amountFeatures){ nextDouble(0.0, 5.0) })
    for (wl in weakLearners){
        wl.makePrediction(testFeatures)
    }
}
