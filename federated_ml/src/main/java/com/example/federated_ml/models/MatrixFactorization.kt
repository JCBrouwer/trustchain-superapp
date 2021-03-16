package com.example.federated_ml.models

import java.lang.Math.sqrt
import java.lang.Math.max
import kotlin.random.Random.Default.nextDouble

class MatrixFactorization(private val numSongs: Int) {
    /*
    global
        k: dimension of factorization approximation
        t: "age" of current model
        Y: k-factorization of songs
        c: song bias vector

    local
        R: user's private ratings of songs (play counts, file ownership, ???, etc)
        X: k-factorization of ratings
        b: rating bias vector
     */
    private val k = 5
    private val minR = 0.0
    private val maxR = 20.0
    private val lr = 0.01
    private val lambda = 0.1
    private var age = Array(numSongs) { _ -> 0.0 }
    private val songMat = Array(numSongs) { _ -> Array(k) { _ -> nextDouble() * sqrt((maxR - minR) / k) } }
    private var songBias = Array(numSongs) { _ -> minR / 2 }
    private val rateMat = Array(numSongs) { _ -> Array(k) { _ -> nextDouble() * sqrt((maxR - minR) / k) } }
    private var rateBias = Array(numSongs) { _ -> minR / 2 }

    fun update(localRatings: Array<Array<Double>>) {
        for (i in rateMat.indices) {
            for (j in defined(localRatings[i]).indices) {
                age[j] += 1
                val err = localRatings[i][j] - rateMat[i] * songMat[j].T - rateBias[i] - songBias[j]
                (songMat[j], rateMat[i]) = Pair( (1 - lr * lambda) * songMat[j] + lr * err * rateMat[i],  (1 - lr * lambda) * rateMat[i] + lr * err * songMat[j] )
                songBias[j] = songBias[j] + lr * err
                rateBias[i] = rateBias[i] + lr * err
            }
        }
    }

    private fun defined(doubles: Array<Double>): Array<Double> {
        // return only gradients where vals are defined
        return doubles
    }

    private fun onReceiveModel(ageNew: Array<Double>, songMatNew: Array<Array<Double>>, songBiasNew: Array<Double>) {
        // age weighted average, more weight to rows which have been updated many times
        for (j in 1..numSongs) {
            if (ageNew[j] != 0.0) {
                val w = ageNew[j] / (age[j] + ageNew[j])
                age[j] = max(age[j], ageNew[j])
                songMat[j] = (1 - w) * songMat[j] + w * songMatNew[j]
                songBias[j] = (1 - w) * songBias[j] + w * songBiasNew[j]
            }
        }
    }

    private fun loss(X,Y,b,c) {
        return 1 / 2 * sum(a[i][j] - b[i] - c[j] sum(x[i][l], y[j][l]))^2 + lambda/2 * norm(X)^2 + lambda / 2 * norm(Y)^2
    }

    private fun gradient() {
        // ???
    }

    private fun train() {
        var ageGather = Array(numSongs) { _ -> 0 }
        var songMatGather = Array(numSongs) { _ -> Array(k) { _ -> 0 } }
        var songBiasGather = Array(numSongs) { _ -> 0 }
        for (node in community.getPeers()) {
            send(node, (age, songMat, songBias))
            val (peerAge, peerSongs, peerSongBias) = receive(node)
            ageGather += peerAge
            songMatGather += peerSongs
            songBiasGather += peerSongBias
        }
        for (j in 1..numSongs) {
            if (ageGather[j] != 0) {
                songMat[j] = songMat[j] + songMatGather[j] / ageGather[j]
                songBias[j] = songBias[j] + songBiasGather[j] / ageGather[j]
                age[j] += 1
            }
        }
    }

    private fun compress() {
        // compress things before sending
    }
}
