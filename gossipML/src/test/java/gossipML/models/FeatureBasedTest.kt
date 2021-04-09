package nl.tudelft.trustchain.gossipML.models

import nl.tudelft.trustchain.gossipML.models.feature_based.Adaline
import nl.tudelft.trustchain.gossipML.models.feature_based.Pegasos
import io.mockk.InternalPlatformDsl.toStr
import nl.tudelft.trustchain.gossipML.Essentia
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random
import kotlin.math.*

class FeatureBasedTest {
    private val amountFeatures = 10
    private var features: Array<Array<Double>> = Array(amountFeatures) { _ -> Array<Double>(amountFeatures) { Random.nextDouble(0.0, 5.0) } }
    private var labels = IntArray(amountFeatures) { Random.nextInt(0, 2) }

    @Test
    fun testPegasos() {
        val model = Pegasos(0.4, amountFeatures, 5)

        model.update(features, labels)

        model.predict(Array(amountFeatures) { Random.nextDouble(0.0, 5.0) })

        val mergeModelEq = Pegasos(0.4, amountFeatures, 5)
        model.merge(mergeModelEq)
        Assert.assertThat(model, instanceOf(Pegasos::class.java))

        val mergeModelDiff = Adaline(0.1, amountFeatures)
        model.merge(mergeModelDiff)
        Assert.assertThat(model, instanceOf(Pegasos::class.java))
    }

    @Test
    fun testAdaline() {
        val model = Adaline(0.1, amountFeatures)

        model.update(features, labels)

        model.predict(Array(amountFeatures) { Random.nextDouble(0.0, 5.0) })

        val mergeModelEq = Adaline(0.1, amountFeatures)
        model.merge(mergeModelEq)
        Assert.assertThat(model, instanceOf(Adaline::class.java))

        val mergeModelDiff = Pegasos(0.4, amountFeatures, 5)
        model.merge(mergeModelDiff)
        Assert.assertThat(model, instanceOf(Adaline::class.java))
    }

    @Test
    fun testAdalinePredictions() {
        val model = Adaline(1.0, 2)
        val biasedFeatures = arrayOf(arrayOf(100.0), arrayOf(-1.0))
        val biasedLabels = intArrayOf(50, 0)

        for (i in 0..100) {
            model.update(biasedFeatures, biasedLabels)
        }

        val biasedTestSamples = arrayOf(arrayOf(100.0), arrayOf(-1.0))

        val res = model.predict(biasedTestSamples)
        Assert.assertTrue(
            "Test Adaline " + res[0].toStr() + ", " + res[1].toStr(),
            res[0] >= res[1]
        )
    }

    private fun pairwiseDifference(models: Array<OnlineModel>): Double {
        var diff = 0.0
        var total = 0.0
        for (m1 in models) {
            for (m2 in models) {
                if (m1 === m2) continue
                for (k in 0 until m1.weights.size) {
                    diff += (m1.weights[k] - m2.weights[k]).absoluteValue
                    total += (m1.weights[k] + m2.weights[k]) / 2
                }
            }
        }
        return diff / total
    }


    @Test
    fun testGossipConvergence() {
        val model1 = Adaline(1.0, 4)
        val model11 = Adaline(1.0, 4)

        val model2 = Pegasos(0.1, 4, 100)
        val model22 = Pegasos(0.1, 4, 100)

        for (i in 0..100) {
            model1.merge(model11)
            model2.merge(model22)

            model11.merge(model1)
            model22.merge(model2)
        }

        val models1 = arrayOf(model1 as OnlineModel, model11 as OnlineModel)
        val models2 = arrayOf(model2 as OnlineModel, model22 as OnlineModel)

        Assert.assertTrue(pairwiseDifference(models1) < 0.1)
        Assert.assertTrue(pairwiseDifference(models2) < 0.1)
    }

    @Test
    fun testRecommendations() {
        val model = Pegasos(0.1, 4, 100)
        val features = arrayOf(
            arrayOf(-1.0, 33.0, -1.0, -1.0),
            arrayOf(-1.0, 224.0, -1.0, -1.0),
            arrayOf(-1.0, -1.0, -1.0, -1.0),
            arrayOf(-1.0, 89.0, -1.0, -1.0),
        )

        val labels = intArrayOf(50, 44, 0, 49)

        for (i in 0..100) {
            model.update(features, labels)
        }

        val test = arrayOf(
            arrayOf(-1.0, 43.0, -1.0, -1.0),
            arrayOf(-1.0, 155.0, -1.0, -1.0),
            arrayOf(-1.0, -1.0, -1.0, -1.0),
            arrayOf(-1.0, 99.0, -1.0, -1.0),
        ).map { it -> (model.predict(it)) }

        Assert.assertTrue(test[0] > test[2])
        Assert.assertTrue(test[1] > test[2])
        Assert.assertTrue(test[3] > test[2])
    }
}
