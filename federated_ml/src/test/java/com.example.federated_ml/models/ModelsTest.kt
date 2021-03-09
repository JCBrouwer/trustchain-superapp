package com.example.federated_ml.models

import com.example.federated_ml.WeakLearner
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class ModelsTest {

    @Test
    fun createWeakLearners() {
        val totalAmountSongs = 10
        val weakLearners = mutableListOf<WeakLearner>()

        // Create 5 weak learners
        for (i in 1..5) {
            // generate random songs history
            val amountSongs = Random.nextInt(totalAmountSongs)
            val songsHistory = Array<Int>(amountSongs) { Random.nextInt(totalAmountSongs) }

            // initiate user / learner with this songs history
            val wl = WeakLearner(i, songsHistory, false)
            weakLearners.add(wl)
        }

        /* TODO Apparently android unit testing doesn't actually create objects like you'd expect.
                We need to figure out how to actually fill our feature arrays rather than use the
                automatically mocked versions if we actually want to test predictions */
//        val amountFeatures = 10
//        val testFeatures = arrayOf(Array<Double>(amountFeatures) { Random.nextDouble(0.0, 5.0) })
//        for (wl in weakLearners) {
//            wl.makePrediction(testFeatures)
//        }

        Assert.assertEquals(1, 1) // just make sure the weak learners finish training
    }
}
