package com.example.federated_ml.ipv8

import android.util.Log
import com.example.federated_ml.models.*
import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
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
open class ModelExchangeMessage @ExperimentalUnsignedTypes constructor(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val modelType: String,
    val model: Model
) : Serializable {

    override fun serialize(): ByteArray {
        Log.w("INIT KEY", originPublicKey.toString())
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(modelType.toByteArray(Charsets.UTF_8)) +
            serializeVarLen(model.serialize().toByteArray(Charsets.UTF_8))
    }

    @kotlin.ExperimentalUnsignedTypes
    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    companion object Deserializer : Deserializable<ModelExchangeMessage> {
        @ExperimentalUnsignedTypes
        @JvmStatic
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<ModelExchangeMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )

            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE

            val (modelTypeBytes, modelTypeSize) = deserializeVarLen(buffer, offset + localOffset)
            val modelType = modelTypeBytes.toString(Charsets.UTF_8)
            localOffset += modelTypeSize
            val (modelBytes, modelSize) = deserializeVarLen(buffer, offset + localOffset)
            val modelJsonStr = modelBytes.toString(Charsets.UTF_8)
            localOffset += modelSize

            val model = if (modelType == "Adaline")
                Json.decodeFromString<Adaline>(modelJsonStr)
            else if (modelType == "Pegasos")
                Json.decodeFromString<Pegasos>(modelJsonStr)
            else
                Json.decodeFromString<PublicMatrixFactorization>(modelJsonStr)

            return Pair(
                first = ModelExchangeMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    modelType = modelType,
                    model = model,
                ), second = localOffset
            )
        }
    }
}
