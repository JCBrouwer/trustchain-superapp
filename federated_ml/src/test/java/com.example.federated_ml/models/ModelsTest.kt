package com.example.federated_ml.models

import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
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
}
