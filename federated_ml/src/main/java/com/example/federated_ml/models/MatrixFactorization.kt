package com.example.federated_ml.models

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Math.*
import kotlin.random.Random.Default.nextDouble

operator fun Array<Double>.plus(other: Double): Array<Double> { for (i in 0 until size) this[i] += other; return this }
operator fun Double.plus(doubles: Array<Double>): Array<Double> { return doubles + this }
operator fun Array<Double>.times(other: Double): Array<Double> { for (i in 0 until size) this[i] *= other; return this }
operator fun Double.times(doubles: Array<Double>): Array<Double> { return doubles * this }
operator fun Array<Double>.div(other: Double): Array<Double> { for (i in 0 until size) this[i] /= other; return this }
operator fun Double.div(doubles: Array<Double>): Array<Double> { return doubles / this }
operator fun Array<Double>.minus(other: Double): Array<Double> { for (i in 0 until size) this[i] -= other; return this }
operator fun Double.minus(doubles: Array<Double>): Array<Double> { return doubles - this }

operator fun Array<Double>.plus(other: Array<Double>): Array<Double> { for (i in 0 until size) this[i] += other[i]; return this }
operator fun Array<Double>.minus(other: Array<Double>): Array<Double> { for (i in 0 until size) this[i] -= other[i]; return this }
operator fun Array<Double>.times(other: Array<Double>): Double {
    var out = 0.0
    for (i in 0 until size) out += this[i] * other[i]
    return out
}

@Serializable
data class PublicMatrixFactorization(
    val age: Array<Double>,
    val songFeatures: Array<Array<Double>>,
    val songBias: Array<Double>): Model("PublicMatrixFactorization") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PublicMatrixFactorization

        if (!age.contentEquals(other.age)) return false
        if (!songFeatures.contentDeepEquals(other.songFeatures)) return false
        if (!songBias.contentEquals(other.songBias)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = age.contentHashCode()
        result = 31 * result + songFeatures.contentDeepHashCode()
        result = 31 * result + songBias.contentHashCode()
        return result
    }
}

@Serializable
class MatrixFactorization(val numSongs: Int): Model("MatrixFactorization") {
    val k = 5
    private val lr = 0.01
    private val lambda = 0.1

    private val minR = 0.0
    private var maxR = 20.0

    private var age = Array(numSongs) { _ -> minR / 2 }
    private var songFeatures = Array(numSongs) { _ -> Array(k) { _ -> nextDouble() * sqrt((maxR - minR) / k) } }
    private var songBias = Array(numSongs) { _ -> minR / 2 }

    private var rateFeatures = Array(numSongs) { _ -> nextDouble() * sqrt((maxR - minR) / k) }
    private var rateBias = minR / 2
    private var ratings = Array(numSongs) { _ -> 0.0 }

    constructor(peerModel: PublicMatrixFactorization) : this(peerModel.songBias.size) {
        this.age = peerModel.age
        this.songFeatures = peerModel.songFeatures
        this.songBias = peerModel.songBias
        update()
    }

    fun onReceiveModel(ageNew: Array<Double>, songFeaturesNew: Array<Array<Double>>, songBiasNew: Array<Double>) {
        merge(ageNew, songFeaturesNew, songBiasNew)
        update()
    }

    private fun update() {
        for (j in ratings.indices) {
            if (ratings[j] != 0.0) {
                val err = ratings[j] - rateFeatures * songFeatures[j] - rateBias - songBias[j]

                val (newSongFeatures, newRateFeatures) = Pair(
                    (1.0 - lr * lambda) * songFeatures[j] + lr * err * rateFeatures,
                    (1.0 - lr * lambda) * rateFeatures + lr * err * songFeatures[j]
                )
                songFeatures[j] = newSongFeatures
                rateFeatures  = newRateFeatures

                songBias[j] += lr * err
                rateBias += lr * err
                age[j] += 1.0
            }
        }
    }

    private fun merge(ageNew: Array<Double>, songFeaturesNew: Array<Array<Double>>, songBiasNew: Array<Double>) {
        // age weighted average, more weight to rows which have been updated many times
        for (j in 1..numSongs) {
            if (ageNew[j] != 0.0) {
                val w = ageNew[j] / (age[j] + ageNew[j])
                age[j] = max(age[j], ageNew[j])
                songFeatures[j] = (1 - w) * songFeatures[j] + w * songFeaturesNew[j]
                songBias[j] = (1 - w) * songBias[j] + w * songBiasNew[j]
            }
        }
    }

    private fun compress(arr: Array<Double>): Array<Double> {
        // TODO compress things before sending
        return arr
    }
    private fun compress(arr: Array<Array<Double>>): Array<Array<Double>> {
        // TODO compress things before sending
        return arr
    }

    // update step implements gradients of the loss() function
    private fun frobnorm(mat: Array<Array<Double>>): Double {
        var sumSq = 0.0
        for (i in mat.indices) {
            for (j in mat[0].indices) {
                sumSq += pow(mat[i][j], 2.0)
            }
        }
        return sqrt(sumSq)
    }
    private fun loss(X: Array<Array<Double>>, Y: Array<Array<Double>>, b: Array<Double>, c: Array<Double>): Double {
        var out = 0.0
        for (i in X.indices) {
            for (j in X[0].indices) {
                var matmul = 0.0
                for (l in X[0].indices) {
                    matmul += X[i][l] * Y[j][l]
                }
                val first = ratings[j] - b[i] - c[j] - matmul
                out += first * first
            }
        }
        val regularization = lambda * (pow(frobnorm(X), 2.0) + pow(frobnorm(Y), 2.0))
        return (out + regularization) / 2.0
    }

    // the function used during automatic serialization in model exchange messages
    // don't serialize private ratings matrix
    override fun serialize(): String {
        return this.serialize(private = false)
    }

    // the function used during serialization for local database storage
    fun serialize(private: Boolean): String {
        Log.i("Recommend", "Serializing MatrixFactorization, including private data: $private")
        return if (private) super.serialize()
        else Json.encodeToString(PublicMatrixFactorization(age, songFeatures, songBias))
    }
}
