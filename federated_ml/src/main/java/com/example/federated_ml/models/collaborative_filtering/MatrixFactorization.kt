package com.example.federated_ml.models.collaborative_filtering

import android.util.Log
import com.example.federated_ml.models.Model
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.lang.Double.NEGATIVE_INFINITY
import kotlin.math.*
import java.util.*
import kotlin.random.Random.Default.nextDouble

object SortedMapSerializer : KSerializer<SortedMap<String, SongFeature>> {
    private val mapSerializer = MapSerializer(String.serializer(), SongFeature.serializer())

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SortedMap<String, SongFeature>) {
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): SortedMap<String, SongFeature> {
        return mapSerializer.deserialize(decoder).toSortedMap()
    }
}

/**
 * TODO
 *
 * @property peerFeatures
 */
@Serializable
data class PublicMatrixFactorization(
    @Serializable(with = SortedMapSerializer::class)
    val peerFeatures: SortedMap<String, SongFeature>
) : Model("PublicMatrixFactorization")

/**
 * TODO
 *
 * @property age
 * @property feature
 * @property bias
 */
@Serializable
data class SongFeature(
    var age: Double,
    var feature: Array<Double>,
    var bias: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SongFeature
        if (age != other.age) return false
        if (!feature.contentEquals(other.feature)) return false
        if (bias != other.bias) return false
        return true
    }

    override fun hashCode(): Int {
        var result = age.hashCode()
        result = 31 * result + feature.contentHashCode()
        result = 31 * result + bias.hashCode()
        return result
    }
}

/**
 * TODO
 *
 * @property feature
 * @property rating
 */
@Serializable
data class RateFeature(
    var feature: Array<Double>,
    var rating: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RateFeature
        if (!feature.contentEquals(other.feature)) return false
        if (rating != other.rating) return false
        return true
    }

    override fun hashCode(): Int {
        var result = feature.contentHashCode()
        result = 31 * result + rating.hashCode()
        return result
    }
}

fun songFeaturesFromArrays(age: Array<Double>, features: Array<Array<Double>>, bias: Array<Double>): Array<SongFeature> {
    return age.zip(features).zip(bias) { (a, b), c -> SongFeature(a, b, c) }.toTypedArray()
}

fun rateFeaturesFromArrays(features: Array<Array<Double>>, ratings: Array<Double>): Array<RateFeature> {
    return features.zip(ratings) { a, b -> RateFeature(a, b) }.toTypedArray()
}

/**
 * TODO
 *
 * @property songNames
 * @property ratings
 */
@Serializable
open class MatrixFactorization(
    private var songNames: Set<String>,
    var ratings: Array<Double>
) : Model("MatrixFactorization") {
    val k = 5

    private val lr = 0.01
    private val lambda = 0.1

    private val minR = 0.0
    private var maxR = 20.0

    @Serializable(with = SortedMapSerializer::class)
    var songFeatures = songNames.zip(
        songFeaturesFromArrays(
            Array(songNames.size) { i -> if (ratings[i] > 0.0) 1.0 else 0.0 }, // ages
            Array(songNames.size) { Array(k) { initFeat() } }, // song features
            Array(songNames.size) { 0.0 } // biases
        )
    ).toMap().toSortedMap()

    @Serializable(with = SortedMapSerializer::class)
    private var rateFeatures = songNames.zip(
        rateFeaturesFromArrays(
            Array(songNames.size) { Array(k) { initFeat() } }, // rate fatures
            ratings // actual ratings
        )
    ).toMap().toSortedMap()
    private var rateBias = 0.0

    constructor(peerModel: PublicMatrixFactorization) : this(peerModel.peerFeatures)
    constructor(peerFeatures: SortedMap<String, SongFeature>) : this(
        songNames = peerFeatures.map { it.key }.toSet(),
        ratings = Array(peerFeatures.size) { 0.0 }
    ) {
        this.songFeatures = peerFeatures.toSortedMap()
    }

    /**
     * TODO
     *
     * @return
     */
    private fun initFeat(): Double {
        return nextDouble() * sqrt((maxR - minR) / k)
    }

    /**
     * TODO
     *
     * @return
     */
    fun predict(): String {
        var bestSong = ""
        var mostRelevant = NEGATIVE_INFINITY
        songFeatures.forEach {
            val (name, triple) = it
            val (_, feature, bias) = triple
            if (rateFeatures[name]!!.rating == 0.0) {
                val relevance = rateFeatures[name]!!.feature * feature + rateBias + bias
                println("$name $relevance")
                if (relevance > mostRelevant) {
                    bestSong = name
                    mostRelevant = relevance
                }
            }
        }
        Log.w("Recommend", "Best colaborative score: $mostRelevant")
        return bestSong
    }

    /**
     * TODO
     *
     * @param newSongNames
     * @param newRatings
     */
    fun updateRatings(newSongNames: Set<String>, newRatings: Array<Double>) {
        val newRatingsMap = newSongNames.zip(newRatings).toMap()
        for (name in newSongNames) {
            if (rateFeatures[name] == null) { // initialize new rating
                rateFeatures[name] = RateFeature(Array(k) { initFeat() }, newRatingsMap[name]!!)

                if (songFeatures[name] == null) { // also initialize newly rated song
                    songFeatures[name] = SongFeature(0.0, Array(k) { initFeat() }, 0.0)
                }
            }
            else { // update rating
//                print("updating $name rating ")
//                print(rateFeatures[name]!!.rating)
//                println(" to ${newRatingsMap[name]}")
                rateFeatures[name]!!.rating = newRatingsMap[name]!!
            }
        }
        update()
    }

    /**
     * TODO
     *
     */
    override fun update() {
        songFeatures.forEach {
            val (name, triple) = it
            val (age, feature, bias) = triple
            if (rateFeatures[name]!!.rating != 0.0) {
                val err = rateFeatures[name]!!.rating - rateFeatures[name]!!.feature * feature - rateBias - bias

//                println("$name $age")
//                println("song [${feature.joinToString()}]")
//                println("rate [${rateFeatures[name]!!.feature.joinToString()}]")
//                println("$err = ${rateFeatures[name]!!.rating} - ${rateFeatures[name]!!.feature * feature} - $rateBias - $bias")

                val (newSongFeature, newRateFeature) = Pair(
                    (1.0 - lr * lambda) * feature + lr * err * rateFeatures[name]!!.feature,
                    (1.0 - lr * lambda) * rateFeatures[name]!!.feature + lr * err * feature
                )

                songFeatures[name]!!.age = age + 1.0

                songFeatures[name]!!.feature = newSongFeature
                rateFeatures[name]!!.feature = newRateFeature

                songFeatures[name]!!.bias += lr * err
                rateBias += lr * err

//                println("song [${songFeatures[name]!!.feature.joinToString()}]")
//                println("rate [${rateFeatures[name]!!.feature.joinToString()}]")
//                println("${rateFeatures[name]!!.rating - rateFeatures[name]!!.feature * songFeatures[name]!!.feature - rateBias - songFeatures[name]!!.bias} = ${rateFeatures[name]!!.rating} - ${rateFeatures[name]!!.feature * songFeatures[name]!!.feature} - $rateBias - ${songFeatures[name]!!.bias}")
//                println()
            }
        }
    }

    /**
     * TODO
     *
     * @param peerModel
     */
    open fun merge(peerModel: PublicMatrixFactorization) {
        merge(peerModel.peerFeatures)
    }

    /**
     * TODO
     *
     * @param peerFeatures
     */
    open fun merge(peerFeatures: SortedMap<String, SongFeature>) {
//        print("peer songs ")
//        for ((k,sf) in peerFeatures)
//            print("$k ${sf.age} ")
//        println()
//        print("my songs ")
//        for ((k,sf) in songFeatures)
//            print("$k ${sf.age} ")
//        println()
        if (peerFeatures.keys.toSet() != songFeatures.keys) {
            for (name in peerFeatures.keys.toSet() + songFeatures.keys.toSet()) {
                // initialize rows not yet present in each map
                if (songFeatures[name] == null) {
                    songFeatures[name] = SongFeature(0.0, Array(k) { initFeat() }, 0.0)
                }
                if (rateFeatures[name] == null) {
                    rateFeatures[name] = RateFeature(Array(k) { initFeat() }, 0.0)
                }
                if (peerFeatures[name] == null) {
                    peerFeatures[name] = SongFeature(0.0, Array(k) { initFeat() }, 0.0)
                }
            }
        }
//        print("peer songs ")
//        for ((k,sf) in peerFeatures)
//            print("$k ${sf.age} ")
//        println()
//        print("my songs ")
//        for ((k,sf) in songFeatures)
//            print("$k ${sf.age} ")
//        println()

        // age weighted average, more weight to rows which have been updated many times
        songFeatures.forEach {
            val (name, triple) = it
            val (age, feature, bias) = triple
            val tripleNew = peerFeatures[name]!!
            val (ageNew, featureNew, biasNew) = tripleNew
//            println(name)
//            println("$age $feature $bias")
//            println("$ageNew $featureNew $biasNew")
//            println("merging with weight ${ageNew / (age + ageNew)} = $ageNew / ($age + $ageNew)")
            if (ageNew != 0.0) {
                val w = ageNew / (age + ageNew)
                songFeatures[name] = SongFeature(
                    age = max(age, ageNew),
                    feature = feature * (1 - w) + featureNew * w,
                    bias = bias * (1 - w) + biasNew * w
                )
            }
        }

        update()
    }

    private fun compress(map: SortedMap<String, SongFeature>): SortedMap<String, SongFeature> {
        /* TODO compress things before sending
         from paper: subsample songFeatures to a fixed number of rows instead of whole thing
         maybe also compress with lz4 or something?
         */
        return map
    }

    // the function used during automatic serialization in model exchange messages
    // don't serialize private ratings matrix
    override fun serialize(): String {
        return this.serialize(private = false)
    }

    // the function used during serialization for local database storage
    fun serialize(private: Boolean): String {
        Log.i(
            "Recommend",
            "Serializing MatrixFactorization, including private data: $private"
        )
        return if (private) Json.encodeToString(this)
        else Json.encodeToString(PublicMatrixFactorization(compress(songFeatures.toSortedMap())))
    }
}
