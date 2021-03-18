package com.example.federated_ml.models
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
open class OnlineModel: Model {
    private val amountFeatures: Int
    internal var weights: Array<Double>

    constructor(amountFeatures: Int, name: String) : super(name) {
        this.amountFeatures = amountFeatures
        this.weights = Array(amountFeatures) { _ -> Random().nextDouble() * 3 }
    }

    fun merge(otherOnlineModel: OnlineModel): OnlineModel {
        for (idx in weights.indices) {
            weights[idx] = (weights[idx] + otherOnlineModel.weights[idx]) / 2
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

    override fun serialize(): String {
        return Json.encodeToString(this)
    }

}
