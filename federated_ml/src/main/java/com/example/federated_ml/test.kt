package com.example.federated_ml

import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

fun main() {
    println("Running...")
    val totalAmountSongs = 10
    val weakLearners = mutableListOf<WeakLearner>()

    // Create 5 weak learners
    for (i in 1..5) {
        // generate random songs history
        val amountSongs = nextInt(totalAmountSongs)
        val songsHistory = Array<Int>(amountSongs) { nextInt(totalAmountSongs) }

        // initiate user / learner with this songs history
        val wl = WeakLearner(i, songsHistory, false)
        weakLearners.add(wl)
    }

    val amountFeatures = 10
    val testFeatures = arrayOf(Array<Double>(amountFeatures) { nextDouble(0.0, 5.0) })
    for (wl in weakLearners) {
        wl.makePrediction(testFeatures)
    }
}
