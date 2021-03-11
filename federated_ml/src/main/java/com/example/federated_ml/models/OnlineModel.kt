package com.example.federated_ml.models
import com.example.federated_ml.ipv8.SerializableSparseArray as SparseArray
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
open class OnlineModel {
    @Contextual
    internal var weights: SparseArray<Double>

    constructor(amountFeatures: Int) {
        weights = SparseArray(amountFeatures)
        for (idx in 1..amountFeatures) {
            weights.append(idx, Random().nextDouble() * 3)
        }
    }

    fun merge(otherOnlineModel: OnlineModel): OnlineModel {
        for (idx in 1..weights.size()) {
            weights.put(idx, (weights.valueAt(idx) + otherOnlineModel.weights.valueAt(idx)) / 2)
        }
        return this
    }

    fun predict(x: Array<Array<Double>>): DoubleArray {
        val result = DoubleArray(x.size)
        for ((idx, item) in x.withIndex()) {
            result[idx] = predict(item)
        }
        return result
    }

    fun score(x: Array<Array<Double>>, y: IntArray): Double {
        var correct = 0.0
        for (i in x.indices) {
            val output = predict(x[i]).toInt()
            if (output == y[i]) {
                correct ++
            }
        }
        return (correct / x.size)
    }

    open fun update(x: Array<Array<Double>>, y: IntArray) {}

    open fun predict(x: Array<Double>): Double {
        return 1.0
    }

    open fun update(x: Array<Double>, y: Int) {}

    open fun serialize(): String {
        return Json.encodeToString(this)
    }
}
