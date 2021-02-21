package com.example.federated_ml

import com.example.federated_ml.feature_engineering.Preprocessing
import com.example.federated_ml.models.OnlineModel
import smile.classification.DiscreteNaiveBayes
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

fun main(){
    println("Running...")
    var weakLearners = mutableListOf<WeakLearner>()

    // Create 5 weak learners
    for(i in 1..5) {
        val wl = WeakLearner(i)
        weakLearners.add(wl)
    }

    val amountFeatures = 10
    val testFeatures = arrayOf(Array<Double>(amountFeatures){ nextDouble(0.0, 5.0) })
    for (wl in weakLearners){
        wl.makePrediction(testFeatures)
    }
}
