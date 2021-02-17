package com.example.federated_ml

import smile.classification.DiscreteNaiveBayes
import smile.classification.SVM
import smile.math.kernel.LinearKernel
import java.sql.Array
import kotlin.random.Random.Default.nextInt

fun main(){
    println("Running...")
    var modelType = "DiscreteNaiveBayes"
    var amountFeatures = 10
    var wl = WeakLearner(1, null, DiscreteNaiveBayes.Model.BERNOULLI, amountFeatures)

    // Update model from 5 "different" instances each has one sample with 10 features
    for(i in 1..5) {
        var features = Preprocessing.preprocessFeatures(arrayOf(IntArray(amountFeatures)
        { nextInt(0, 11) }), modelType)
        var labels = Preprocessing.preprocessLabels(IntArray(amountFeatures) { nextInt(0, 2) })

        wl.retrainWithNewData(features, labels)
    }

    var testFeatures = Preprocessing.preprocessFeatures(arrayOf(IntArray(amountFeatures) { it * 1 }), modelType)
    wl.makePrediction(testFeatures)
}
