package nl.tudelft.trustchain.gossipML.models

import nl.tudelft.trustchain.gossipML.models.feature_based.Adaline
import nl.tudelft.trustchain.gossipML.models.feature_based.Pegasos
import io.mockk.InternalPlatformDsl.toStr
import nl.tudelft.trustchain.gossipML.Essentia
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

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
    fun testPegasosPredictions() {
        val model = Pegasos(0.1, 1, 100)
        val biasedFeatures = arrayOf(arrayOf(100.0), arrayOf(-1.0))
        val biasedLabels = intArrayOf(50, 0)

        for (i in 0..1000) {
            model.update(biasedFeatures, biasedLabels)
        }

        val biasedTestSamples = arrayOf(arrayOf(100.0), arrayOf(-1.0))

        val res = model.predict(biasedTestSamples)
        Assert.assertTrue(
            "Test Pegasos " + res[0].toStr() + ", " + res[1].toStr(),
            res[0] >= res[1]
        )
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

        for (i in 0..1000) {
            model.update(biasedFeatures, biasedLabels)
        }

        val biasedTestSamples = arrayOf(arrayOf(100.0), arrayOf(-1.0))

        val res = model.predict(biasedTestSamples)
        Assert.assertTrue(
            "Test Adaline " + res[0].toStr() + ", " + res[1].toStr(),
            res[0] >= res[1]
        )
    }
}
