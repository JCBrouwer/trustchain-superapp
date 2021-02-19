package com.example.federated_ml

import com.example.federated_ml.feature_engineering.Preprocessing
import smile.classification.DiscreteNaiveBayes
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

fun main(){
    println("Running...")
    var amountFeatures = 10
    var amountSongs = 10
    var wl = WeakLearner(1, amountFeatures)

    // Update model
    for(i in 1..5) {
        var features = Array(amountSongs) {_ -> Array<Double>(amountFeatures){ nextDouble(0.0, 5.0) }}
        var labels = IntArray(amountSongs) { nextInt(0, 2) }

        wl.retrainWithNewData(features, labels)
    }

    var testFeatures = arrayOf(Array<Double>(amountFeatures){ nextDouble(0.0, 5.0) })
    wl.makePrediction(testFeatures)
}
