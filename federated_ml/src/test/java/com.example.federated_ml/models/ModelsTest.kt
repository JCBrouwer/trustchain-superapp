package com.example.federated_ml.models

import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
import com.example.federated_ml.models.collaborative_filtering.PublicMatrixFactorization
import com.example.federated_ml.models.collaborative_filtering.SongFeature
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
    fun testCreateAndUpdateMF() {
        val model = MatrixFactorization(emptySet(), emptyArray())

        model.merge(
            sortedMapOf(
                Pair("a", SongFeature(5.0, Array(5) { Random.nextDouble(1.0, 5.0) }, 0.0)),
                Pair("b", SongFeature(2.0, Array(5) { Random.nextDouble(0.0, 3.0) }, 0.0)),
                Pair("c", SongFeature(7.0, Array(5) { Random.nextDouble(0.0, 8.0) }, 0.0)),
            )
        )

        Assert.assertThat(model, instanceOf(MatrixFactorization::class.java))
        Assert.assertEquals(model.songFeatures.size, 3)
    }

    @Test
    fun testMFPredictions() {
        val pubModel = PublicMatrixFactorization(
            sortedMapOf(
                Pair("good", SongFeature(1.0, Array(5) { Random.nextDouble(1.0, 5.0) }, 0.0)),
                Pair("bad", SongFeature(1.0, Array(5) { 0.0 }, 0.0)), // nobody likes this song
            )
        )
        val model = MatrixFactorization(pubModel)
        val pred = model.predict()
        Assert.assertEquals(pred, "good")
    }

    @Test
    fun testMFRatingsUpdate() {
        val model = MatrixFactorization(
            sortedMapOf(
                Pair("good", SongFeature(1.0, Array(5) { Random.nextDouble(1.0, 5.0) }, 0.0)),
                Pair("bad", SongFeature(1.0, Array(5) { 0.0 }, 0.0)), // nobody likes this song
            )
        )

        model.updateRatings(
            setOf("good", "bad"),
            arrayOf(0.0, 1.0) // except for me apparently
        )

        Assert.assertNotEquals(model.songFeatures["bad"] , Array(5) { 0.0 })
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

//    @Test
//    fun testPegasosPredictions() {
//        val model = Pegasos(0.1, 2, 10)
//        val biasedFeatures =  arrayOf(arrayOf(100.0, -1.0), arrayOf(-1.0, 100.0))
//        val biasedLabels = intArrayOf(50, 0)
//
//        for (i in 0..1000) {
//            model.update(biasedFeatures, biasedLabels)
//        }
//
//        val biasedTestSamples = arrayOf(arrayOf(-1.0, 99.0), arrayOf(99.0, -1.0))
//
//        val res = model.predict(biasedTestSamples)
//        Assert.assertTrue(res[0] < res[1])
//    }

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

//    @Test
//    fun testAdalinePredictions() {
//        val model = Adaline(0.1, 2)
//        val biasedFeatures =  arrayOf(arrayOf(100.0, -1.0), arrayOf(-1.0, 100.0))
//        val biasedLabels = intArrayOf(50, 0)
//
//        for (i in 0..1000) {
//            model.update(biasedFeatures, biasedLabels)
//        }
//
//        val biasedTestSamples = arrayOf(arrayOf(-1.0, 99.0), arrayOf(99.0, -1.0))
//
//        val res = model.predict(biasedTestSamples)
//         Assert.assertTrue(res[0] < res[1])
//    }
}
