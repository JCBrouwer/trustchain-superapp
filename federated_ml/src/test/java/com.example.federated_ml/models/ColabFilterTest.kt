package com.example.federated_ml.models

import com.example.federated_ml.models.collaborative_filtering.*
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ColabFilterTest {

    @Test
    fun testCreateAndUpdate() {
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
    fun testPredictions() {
        val pubModel = PublicMatrixFactorization(
            sortedMapOf(
                Pair("good", SongFeature(1.0, Array(5) { Random.nextDouble(2.0, 5.0) }, 0.0)),
                Pair("bad", SongFeature(1.0, Array(5) { 1.0 }, 0.0)), // nobody likes this song
            )
        )
        val model = MatrixFactorization(pubModel)
        val pred = model.predict()
        Assert.assertEquals(pred, "good")
    }

    @Test
    fun testRatingsUpdate() {
        val model = MatrixFactorization(
            sortedMapOf(
                Pair("good", SongFeature(1.0, Array(5) { Random.nextDouble(2.0, 5.0) }, 0.0)),
                Pair("bad", SongFeature(1.0, Array(5) { 1.0 }, 0.0)), // nobody likes this song
            )
        )

        model.updateRatings(
            setOf("good", "bad"),
            arrayOf(0.0, 1.0) // except for me apparently
        )

        Assert.assertNotEquals(model.songFeatures["bad"], Array(5) { 0.0 })
    }

    @Test
    fun testSyncModels() {
        val pubModel = PublicMatrixFactorization(
            sortedMapOf(
                Pair("good", SongFeature(1.0, Array(5) { Random.nextDouble(2.0, 5.0) }, 0.0)),
                Pair("bad", SongFeature(1.0, Array(5) { 1.0 }, 0.0)), // nobody likes this song
            )
        )
        val models = Array(3) {MatrixFactorization(pubModel)}

        val updatedModel = MatrixFactorization(pubModel)
        updatedModel.updateRatings(setOf("new"), arrayOf(5.0))

        // gossip new song to peers
        for (model in models) {
            model.merge(PublicMatrixFactorization(updatedModel.songFeatures))
            Assert.assertTrue(model.songFeatures.keys.contains("new"))
        }
    }

    private fun pairwiseDifference(models: Array<MatrixFactorization>): Double{
        var diff = 0.0
        var total = 0.0
        for (m1 in models) {
            for (m2 in models) {
                if (m1 === m2) continue
                for (k in m1.songFeatures.keys) {
                    diff += abs((m1.songFeatures[k]!!.feature - m2.songFeatures[k]!!.feature).sum())
                    total += (m1.songFeatures[k]!!.feature + m2.songFeatures[k]!!.feature).sum() / 2
                }
            }
        }
        return diff / total
    }

    @Test
    fun testGossipConvergence() {
        val models = arrayOf(
            // a fans
            MatrixFactorization(setOf("a","aa"), arrayOf(5.0, 10.0)),
            MatrixFactorization(setOf("aa","aaa","b"), arrayOf(7.0,4.0,1.0)),
            // b fans
            MatrixFactorization(setOf("b","a"), arrayOf(7.0,1.0)),
            MatrixFactorization(setOf("bb","bbb","b"), arrayOf(7.0,6.0,8.0)),
            MatrixFactorization(setOf("bb","bbb","c"), arrayOf(5.0,10.0,1.0)),
            // c fans
            MatrixFactorization(setOf("c","cc"), arrayOf(5.0,10.0)),
            MatrixFactorization(setOf("ccc","cc","a"), arrayOf(7.0,4.0,1.0)),
            // d fan
            MatrixFactorization(setOf("d","dd"), arrayOf(10.0,10.0))
        )

        // gossip models iteratively until (hopefully) convergence
        for (round in 1..10) {
            for (m1 in models) {
                for (m2 in models) {
                    if (m1 === m2) continue
                    m2.merge(PublicMatrixFactorization(m1.songFeatures.toSortedMap()))
                }
            }
            println("round $round   % diff: ${pairwiseDifference(models)}")
        }
        Assert.assertTrue(pairwiseDifference(models) < 0.001)
    }

//    @Test
//    fun testRecommendations() {
//        val models = arrayOf(
//            // a fans
//            MatrixFactorization(setOf("a","aa"), arrayOf(5.0, 10.0)),
//            MatrixFactorization(setOf("aa","aaa","b"), arrayOf(7.0,4.0,1.0)),
//            // b fans
//            MatrixFactorization(setOf("b","a"), arrayOf(7.0,1.0)),
//            MatrixFactorization(setOf("d","bbb","b"), arrayOf(1.0,6.0,8.0)),
//            MatrixFactorization(setOf("bb","bbb","c"), arrayOf(5.0,10.0,1.0)),
//            // c fans
//            MatrixFactorization(setOf("c","cc"), arrayOf(5.0,10.0)),
//            MatrixFactorization(setOf("ccc","cc","a"), arrayOf(7.0,4.0,1.0)),
//            // d fan
//            MatrixFactorization(setOf("d","dd"), arrayOf(10.0,10.0))
//        )
//
//        // gossip models iteratively until (hopefully) convergence
//        println("gossiping\n")
//        for (round in 1..1) {
//            for (m1 in models) {
//                for (m2 in models) {
//                    if (m1 === m2) continue
//                    m2.merge(PublicMatrixFactorization(m1.songFeatures.toSortedMap()))
//                }
//            }
//        }
//
//        println("aaa ${models[0].predict()}")
//        println("a   ${models[1].predict()}")
//        println("bbb ${models[2].predict()}")
//        println("bb  ${models[3].predict()}")
//        println("b   ${models[4].predict()}")
//        println("ccc ${models[5].predict()}")
//        println("c   ${models[6].predict()}")
//        println("b   ${models[7].predict()}")
//
//        println()
//        for ((k,sf) in models[0].songFeatures) {
//            print(k)
//            for (d in sf.feature) {
//                print(" $d")
//            }
//            println()
//        }
//
//        Assert.assertEquals("aaa", models[0].predict())
//        Assert.assertEquals("a", models[1].predict())
//        Assert.assertEquals("bbb", models[2].predict())
//        Assert.assertEquals("bb", models[3].predict())
//        Assert.assertEquals("b", models[4].predict())
//        Assert.assertEquals("ccc", models[5].predict())
//        Assert.assertEquals("c", models[6].predict())
//        Assert.assertEquals("b", models[7].predict())
//    }
}
