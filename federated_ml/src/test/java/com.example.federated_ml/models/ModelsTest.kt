package com.example.federated_ml.models

import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
import com.example.federated_ml.models.collaborative_filtering.PublicMatrixFactorization
import com.example.federated_ml.models.feature_based.Adaline
import com.example.federated_ml.models.feature_based.Pegasos
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class ModelsTest {
    private val amountFeatures = 10
    private var features: Array<Array<Double>> = Array(amountFeatures) { _ -> Array<Double>(amountFeatures) { Random.nextDouble(0.0, 5.0) } }
    private var labels = IntArray(amountFeatures) { Random.nextInt(0, 2) }

    @Test
    fun testMF() {
        val model = MatrixFactorization(
            numSongs = 0,
            songNames = HashSet<String>(0),
            ratings = Array<Double>(0) { _ -> 0.0 }
        )

        model.merge(
            Array(5) { Random.nextDouble(0.0, 5.0) },
            sortedMapOf(
                Pair("c", Array(5) { Random.nextDouble(0.0, 5.0) }),
                Pair("b", Array(5) { Random.nextDouble(0.0, 5.0) }),
                Pair("d", Array(5) { Random.nextDouble(0.0, 5.0) })
            ),
            Array(5) { Random.nextDouble(0.0, 5.0) }
        )
        model.update()
        Assert.assertThat(model, instanceOf(MatrixFactorization::class.java))
    }

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
    fun testMFPredictions() {
        val pubModel = PublicMatrixFactorization(arrayOf(0.0, 0.0),
            sortedMapOf(
                Pair("BiasedSong", arrayOf(1.0, 0.0)),
                Pair("Random", arrayOf(0.0, 1.0))
            ),
            arrayOf(0.0, 0.0))
        val model = MatrixFactorization(pubModel)

        model.merge(
            arrayOf(1.0, 1.0),
            sortedMapOf(
                Pair("BiasedSong", arrayOf(1.0, 1.0)),
                Pair("Suggestion", arrayOf(1.0, 1.0))
            ),
            arrayOf(0.0, 0.0)
        )
        model.update()
        val pred = model.predict()
        Assert.assertEquals(pred, "Suggestion")
    }

    @Test
    fun testPegasosPredictions() {
        val model = Pegasos(0.1, 2, 10)
        val biasedFeatures =  arrayOf(arrayOf(100.0, -1.0), arrayOf(-1.0, 100.0))
        val biasedLabels = intArrayOf(50, 0)

        for (i in 0..1000) {
            model.update(biasedFeatures, biasedLabels)
        }

        val biasedTestSamples = arrayOf(arrayOf(-1.0, 99.0), arrayOf(99.0, -1.0))

        val res = model.predict(biasedTestSamples)
         Assert.assertTrue(res[0] < res[1])
    }

    @Test
    fun testAdalinePredictions() {
        val model = Adaline(0.1, 2)
        val biasedFeatures =  arrayOf(arrayOf(100.0, -1.0), arrayOf(-1.0, 100.0))
        val biasedLabels = intArrayOf(50, 0)

        for (i in 0..1000) {
            model.update(biasedFeatures, biasedLabels)
        }

        val biasedTestSamples = arrayOf(arrayOf(-1.0, 99.0), arrayOf(99.0, -1.0))

        val res = model.predict(biasedTestSamples)
         Assert.assertTrue(res[0] < res[1])
    }
}
