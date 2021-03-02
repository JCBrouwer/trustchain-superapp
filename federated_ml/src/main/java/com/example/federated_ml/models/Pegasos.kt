package com.example.federated_ml.models
import java.util.*

class Pegasos(regularization: Double, amountFeatures: Int, iterations: Int) :
    OnlineModel(amountFeatures) {
    private val regularization = regularization
    private val iterations = iterations

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
        var weightedSum = 0.0
        for (pair in this.weights.zip(x)) {
            weightedSum += pair.first * pair.second
        }
        return weightedSum
    }

    override fun predict(x: Array<Double>): Int {
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
            for (idx in weights.indices) {
                weights[idx] = (1 - eta * regularization) * weights[idx] + eta * y
            }
        } else {
            for (idx in weights.indices) {
                weights[idx] *= (1 - eta * regularization)
            }
        }
    }
}
