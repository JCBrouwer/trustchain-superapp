package nl.tudelft.trustchain.gossipML.models.feature_based
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.tudelft.trustchain.gossipML.models.OnlineModel
import java.util.*

/**
 * Pegasos Machine Learning model (Primal Estimated sub-gradient solver for SVM)
 */
@Serializable
class Pegasos : OnlineModel {
    private val regularization: Double
    private val iterations: Int

    constructor(regularization: Double, amountFeatures: Int, iterations: Int) : super(amountFeatures, "Pegasos") {
        this.regularization = regularization
        this.iterations = iterations
    }

    /**
     * update model with single data instance
     *
     * @param x - feature array
     * @param y - label
     */
    override fun update(x: Array<Double>, y: Int) {
        val eta = 1.0 / regularization
        gradientSVM(x, y, eta)
    }

    /**
     * update model with multiple data instances
     *
     * @param x - array of feature arrays
     * @param y - array of labels
     */
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

    /**
     * Pegasos activation function used in predictions
     *
     * @param x output of SVM model
     * @return activation of x
     */
    private fun activation(x: Double): Double {
        return x
    }

    /**
     * weighted sum of all features of an instance
     *
     * @param x feature array
     * @return weighted sum of input features of an instance
     */
    fun weightedSum(x: Array<Double>): Double {
        var totalSum = 0.0
        for (idx in x.indices) {
            totalSum += this.weights[idx] * x[idx]
        }
        return totalSum
    }

    /**
     * predict score for a given data instance
     *
     * @param x feature array
     * @return predicted score
     */
    override fun predict(x: Array<Double>): Double {
        val weightedSum = weightedSum(x)
        return activation(weightedSum)
    }

    /**
     * binary classification of input instance based on activation
     * e.g. - like / dislike song
     *
     * @param x feature array
     * @return binary predicted label
     */
    fun classify(x: Array<Double>): Int {
        val weightedSum = weightedSum(x)
        return if (activation(weightedSum) >= 0.0) {
            1
        } else {
            0
        }
    }

    /**
     * learning function of Pegasos, updates model weights
     *
     * @param x - features
     * @param y - label
     * @param eta - step value
     */
    private fun gradientSVM(x: Array<Double>, y: Int, eta: Double) {
        val score = weightedSum(x)
        if (y * score < 1) {
            for (idx in 0 until weights.size) {
                weights[idx] = (1 - eta * regularization) * weights[idx] + eta * y * x[idx]
            }
        } else {
            for (idx in 0 until weights.size) {
                weights[idx] *= (1 - eta * regularization)
            }
        }
    }

    override fun serialize(): String {
        return Json.encodeToString(this)
    }
}
