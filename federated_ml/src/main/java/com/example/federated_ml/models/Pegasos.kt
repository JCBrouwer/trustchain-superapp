package com.example.federated_ml.models
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.*

@Serializable
class Pegasos : OnlineModel {
    private val regularization: Double
    private val iterations: Int
    override var name = "Pegasos"

    constructor(regularization: Double, amountFeatures: Int, iterations: Int) : super(amountFeatures) {
        this.regularization = regularization
        this.iterations = iterations
    }

    override fun update(x: Array<Double>, y: Int) {
        val eta = 1.0 / regularization
        gradientSVM(x, y, eta)
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {
        require(x.size == y.size) {
            String.format("Input vector x of size %d not equal to length %d of y", x.size, y.size)
        }

        for (iteration in 0..iterations) {
            val i = Random().nextInt(x.size)
            val eta = 1.0 / (regularization * (i + 1))

            gradientSVM(x[i], y[i], eta)
        }
    }

    private fun activation(x: Double): Double {
        return x
    }

    fun weightedSum(x: Array<Double>): Double {
        var totalSum = 0.0
        for (idx in x.indices) {
            totalSum += this.weights[idx] * x[idx]
        }
        return totalSum
    }

    override fun predict(x: Array<Double>): Double {
        val weightedSum = weightedSum(x)
        return activation(weightedSum)
    }

    fun classify(x: Array<Double>): Int {
        val weightedSum = weightedSum(x)
        return if (activation(weightedSum) >= 0.0) {
            1
        } else {
            0
        }
    }

    private fun gradientSVM(x: Array<Double>, y: Int, eta: Double) {
        val score = weightedSum(x)
        if (y * score < 1) {
            for (idx in 0 until weights.size) {
                weights[idx] = (1 - eta * regularization) * weights[idx] + eta * y
            }
        } else {
            for (idx in 0 until weights.size) {
                weights[idx] *= (1 - eta * regularization)
            }
        }
    }

    override fun serialize(): String {
//        val jsn = JSONObject("""{"weights":$weights, "iterations":$iterations,
//            |"regularization:$regularization"}""".trimMargin())
        return Json.encodeToString(this)
    }
}
