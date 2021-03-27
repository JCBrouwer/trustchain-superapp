package com.example.federated_ml.ipv8

import android.util.Log
import com.example.federated_ml.models.*
import com.example.federated_ml.models.collaborative_filtering.PublicMatrixFactorization
import com.example.federated_ml.models.feature_based.Adaline
import com.example.federated_ml.models.feature_based.Pegasos
import kotlin.UInt
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.messaging.*

/**
 * This is a message from a peer sending and asking for a model from other peers
 */
open class FeaturesExchangeMessage @ExperimentalUnsignedTypes constructor(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val songIdentifier: String,
    val features: String
) : Serializable {

    override fun serialize(): ByteArray {
        Log.w("INIT KEY", originPublicKey.toString())
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(songIdentifier.toByteArray(Charsets.UTF_8)) +
            serializeVarLen(features.toByteArray(Charsets.UTF_8))
    }

    @kotlin.ExperimentalUnsignedTypes
    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    companion object Deserializer : Deserializable<FeaturesExchangeMessage> {
        @ExperimentalUnsignedTypes
        @JvmStatic
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<FeaturesExchangeMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )

            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE

            val (songIdentifierBytes, songIdentifierSize) = deserializeVarLen(buffer, offset + localOffset)
            val songIdentifier = songIdentifierBytes.toString(Charsets.UTF_8)
            localOffset += songIdentifierSize
            val (featuresBytes, featuresSize) = deserializeVarLen(buffer, offset + localOffset)
            val features = featuresBytes.toString(Charsets.UTF_8)
            localOffset += featuresSize

            return Pair(
                first = FeaturesExchangeMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    songIdentifier = songIdentifier,
                    features = features
                ),
                second = localOffset
            )
        }
    }
}
