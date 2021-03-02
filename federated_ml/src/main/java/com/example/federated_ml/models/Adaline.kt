package com.example.federated_ml.models
import java.util.*

class Adaline(learningRate: Double, amountFeatures: Int) :
    OnlineModel(amountFeatures) {
    private val learningRate = learningRate
    var bias = Random().nextDouble()

    override fun update(x: Array<Double>, y: Int) {
        val error = y - activation(forward(x))
        this.bias += this.learningRate * error
        for ((idx, item) in x.withIndex()) {
            weights[idx] += learningRate * error * item
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

    override fun predict(x: Array<Double>): Int {
        return if (activation(forward(x)) >= 0.0) {
            1
        } else {
            0
        }
    }

    // Linear activation function for now
    private fun activation(x: Double): Double {
        return x
    }

    private fun forward(x: Array<Double>): Double {
        var weightedSum = this.bias
        for (pair in this.weights.zip(x)) {
            weightedSum += pair.first * pair.second
        }

        return weightedSum
    }
}
