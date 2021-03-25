package com.example.federated_ml.models.collaborative_filtering

import kotlinx.serialization.Serializable
import com.example.federated_ml.models.Model
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

operator fun Array<Double>.plus(other: Double): Array<Double> {
    for (i in 0 until size) this[i] += other
    return this
}

operator fun Double.plus(doubles: Array<Double>): Array<Double> {
    return doubles + this
}

operator fun Array<Double>.times(other: Double): Array<Double> {
    for (i in 0 until size) this[i] *= other
    return this
}

operator fun Double.times(doubles: Array<Double>): Array<Double> {
    return doubles * this
}

operator fun Array<Double>.div(other: Double): Array<Double> {
    for (i in 0 until size) this[i] /= other
    return this
}

operator fun Double.div(doubles: Array<Double>): Array<Double> {
    return doubles / this
}

operator fun Array<Double>.minus(other: Double): Array<Double> {
    for (i in 0 until size) this[i] -= other
    return this
}

operator fun Double.minus(doubles: Array<Double>): Array<Double> {
    return doubles - this
}

operator fun Array<Double>.plus(other: Array<Double>): Array<Double> {
    for (i in 0 until size) this[i] += other[i]
    return this
}

operator fun Array<Double>.minus(other: Array<Double>): Array<Double> {
    for (i in 0 until size) this[i] -= other[i]
    return this
}

operator fun Array<Double>.times(other: Array<Double>): Double {
    var out = 0.0
    for (i in 0 until size) out += this[i] * other[i]
    return out
}

object SortedMapSerializer : KSerializer<SortedMap<String, Array<Double>>> {
    private val mapSerializer = MapSerializer(String.serializer(), ArraySerializer(Double.serializer()))

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SortedMap<String, Array<Double>>) {
        mapSerializer.serialize(encoder, value.toSortedMap())
    }

    override fun deserialize(decoder: Decoder): SortedMap<String, Array<Double>> {
        return mapSerializer.deserialize(decoder).toSortedMap()
    }
}

@Serializable
data class PublicMatrixFactorization(
    var age: Array<Double>,
    @Serializable(with = SortedMapSerializer::class)
    var songFeaturesMap: SortedMap<String, Array<Double>>,
    var songBias: Array<Double>
) : Model("PublicMatrixFactorization") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PublicMatrixFactorization

        if (!age.contentEquals(other.age)) return false
        if (songFeaturesMap != other.songFeaturesMap) return false
        if (!songBias.contentEquals(other.songBias)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = age.contentHashCode()
        result = 31 * result + songFeaturesMap.hashCode()
        result = 31 * result + songBias.contentHashCode()
        return result
    }
}
