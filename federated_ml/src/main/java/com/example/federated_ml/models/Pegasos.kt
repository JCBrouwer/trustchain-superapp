package com.example.federated_ml.models
import smile.classification.OnlineClassifier
import java.util.*


class Pegasos(regularization: Float, amountFeatures: Int, iterations: Int): OnlineClassifier<Array<Double>> {
    private val regularization = regularization
    private val iterations = iterations
    private var weights: Array<Double> = Array(amountFeatures) { _ -> Random().nextDouble() * 3}

    set(value) {
        field = value
    }
    get() = field

    override fun update(x: Array<Double>, y: Int){
        var eta = 1.0 / regularization
        gradientSVM(x, y, eta)
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {
        require(x.size == y.size) {
            String.format("Input vector x of size %d not equal to length %d of y", x.size, y.size)
        }

        for (iteration in 0..iterations){
            var i = Random().nextInt(x.size)
            var eta = 1.0 / (regularization * (i + 1))

            gradientSVM(x[i], y[i], eta)
        }
    }

    override fun predict(x: Array<Array<Double>>): IntArray {
        val result  = IntArray(x.size)
        for((idx, item) in x.withIndex()){
            result[idx] = predict(item)
        }

        return result
    }

    private fun activation(x: Double): Double {
        return x
    }

    fun weightedSum(x: Array<Double>): Double{
        var weightedSum = 0.0
        for(pair in this.weights.zip(x)){
            weightedSum += pair.first * pair.second
        }
        return weightedSum
    }


    override fun predict(x: Array<Double>): Int {
        var weightedSum = weightedSum(x)

        if (activation(weightedSum) >= 0.0) {
            return 1
        } else {
            return 0
        }
    }

    private fun gradientSVM(x: Array<Double>, y: Int, eta: Double) {
        var score = weightedSum(x)
        if (y * score < 1){
            for (idx in weights.indices){
                weights[idx] =  (1 - eta * regularization) * weights[idx] + eta * y
            }
        } else {
            for (idx in weights.indices){
                weights[idx] *=  (1 - eta * regularization)
            }
        }
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

