package com.example.federated_ml.models
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON

import smile.classification.OnlineClassifier
import java.util.*
import kotlin.collections.HashMap

open class OnlineModel (amountFeatures: Int): OnlineClassifier<Array<Double>> {
    internal var weights: Array<Double> = Array(amountFeatures) { _ -> Random().nextDouble() * 3}

    fun merge(otherOnlineModel: OnlineModel): OnlineModel{
        for (idx in weights.indices){
            weights[idx] = (weights[idx] + otherOnlineModel.weights[idx]) / 2
        }
        return this
    }

    override fun predict(x: Array<Array<Double>>): IntArray {
        val result  = IntArray(x.size)
        for((idx, item) in x.withIndex()){
            result[idx] = predict(item)
        }

        return result
    }

    fun score(x: Array<Array<Double>>, y: IntArray): Double{
        var correct = 0.0
        for (i in x.indices) {
            var output = predict(x[i])
            if (output == y[i]) {
                correct ++
            }
        }

        return (correct / x.size)
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {}

    override fun predict(x: Array<Double>): Int {
        return 1
    }

    override fun update(x: Array<Double>, y: Int) {}

    fun serialize(): String {
        val weightString = JSON.stringify(Array.serializer(), weights)
        return JSON.stringify(HashMap.serializer(), HashMap<String, String>("weights": weightString))
    }
}
