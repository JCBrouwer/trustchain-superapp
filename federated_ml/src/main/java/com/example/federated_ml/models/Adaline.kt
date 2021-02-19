package com.example.federated_ml.models
import smile.classification.OnlineClassifier
import java.util.*


class Adaline(learningRate: Double, amountFeatures: Int): OnlineClassifier<Array<Double>>{
    private val learningRate = learningRate
    private var weights: Array<Double> = Array(amountFeatures) { _ -> Random().nextDouble()}
    private var bias = Random().nextDouble()

    override fun update(x: Array<Double>, y: Int){
        var error = y - activation(forward(x))
        this.bias += this.learningRate * error
        for((idx, item) in x.withIndex()){
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

    override fun predict(x: Array<Array<Double>>): IntArray {
        val result  = IntArray(x.size)
        for((idx, item) in x.withIndex()){
            result[idx] = predict(item)
        }

        return result
    }

    override fun predict(x: Array<Double>): Int {
        if (activation(forward(x)) >= 0.0) {
            return 1
        } else {
            return 0
        }
    }

    // Linear activation function for now
    private fun activation(x: Double): Double {
        return x
    }

    private fun forward(x: Array<Double>): Double {
        var weightedSum = this.bias
        for(pair in this.weights.zip(x)){
            weightedSum += pair.first * pair.second
        }

        return weightedSum
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

}

