package com.example.federated_ml.models
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.*

@Serializable
class Adaline: OnlineModel {
    private val learningRate: Double
    var bias = Random().nextDouble()
    override var name = "Adaline"

    constructor(learningRate: Double, amountFeatures: Int) : super(amountFeatures) {
        this.learningRate = learningRate
    }

    override fun update(x: Array<Double>, y: Int) {
        val error = y - activation(forward(x))
        this.bias += this.learningRate * error
        for ((idx, item) in x.withIndex()) {
            weights[idx] = weights[idx] + learningRate * error * item
        }
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {
        require(x.size == y.size) {
            String.format("Input vector x of size %d not equal to length %d of y", x.size, y.size)
        }
        for (i in x.indices) {
            update(x[i], y[i])
        }
    }

    override fun predict(x: Array<Double>): Double {
        return activation(forward(x))
    }

    fun classify(x: Array<Double>): Double {
        return if (activation(forward(x)) >= 0.0) {
            1.0
        } else {
            0.0
        }
    }

    // Linear activation function for now
    private fun activation(x: Double): Double {
        return x
    }

    private fun forward(x: Array<Double>): Double {
        var weightedSum = this.bias
        for (idx in 1..x.size) {
            weightedSum += this.weights[idx] * x[idx]
        }
        return weightedSum
    }

    override fun serialize(): String {
//        val jsn = JSONObject("""{"weights":$weights, "bias":$bias,
//            |"learningRate:$learningRate"}""".trimMargin())
        return Json.encodeToString(this)
    }
}
