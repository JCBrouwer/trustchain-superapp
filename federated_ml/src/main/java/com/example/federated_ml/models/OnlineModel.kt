package com.example.federated_ml.models
import android.util.SparseArray
import androidx.core.util.set
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*

@kotlinx.serialization.Serializable
open class OnlineModel {
    internal var weights: SparseArray<Double>

    constructor(amountFeatures: Int) {
        weights = SparseArray(amountFeatures)
        for (idx in 1..amountFeatures) {
            weights.append(idx, Random().nextDouble() * 3 )
        }
    }

    fun merge(otherOnlineModel: OnlineModel): OnlineModel {
        for (idx in 1..weights.size()) {
            weights.put(idx, (weights.valueAt(idx) + otherOnlineModel.weights.valueAt(idx)) / 2)
        }
        return this
    }

    fun predict(x: Array<Array<Double>>): IntArray {
        val result = IntArray(x.size)
        for ((idx, item) in x.withIndex()) {
            result[idx] = predict(item)
        }

        return result
    }

    fun score(x: Array<Array<Double>>, y: IntArray): Double {
        var correct = 0.0
        for (i in x.indices) {
            val output = predict(x[i])
            if (output == y[i]) {
                correct ++
            }
        }

        return (correct / x.size)
    }

    fun update(x: Array<Array<Double>>, y: IntArray) {}

    fun predict(x: Array<Double>): Int {
        return 1
    }

    fun update(x: Array<Double>, y: Int) {}

    @ImplicitReflectionSerializer
    @kotlinx.serialization.UnstableDefault
    open fun serialize(): String {
        return Json.toJson(this).toString()
    }
}
