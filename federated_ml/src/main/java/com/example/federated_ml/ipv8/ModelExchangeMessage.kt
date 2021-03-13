package com.example.federated_ml.ipv8

import android.util.Log
import kotlin.UInt
import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.messaging.*

/**
 * This is a message from a peer sending and asking for a model from other peers
 */
open class ModelExchangeMessage @ExperimentalUnsignedTypes constructor(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val modelType: String,
    val model: OnlineModel
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
            Log.w("!!!!!!!!!!!", originPublicKey.toString())
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)

            localOffset += SERIALIZED_UINT_SIZE
            val (modelType, modelTypeSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelTypeSize
            val (model, modelSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelSize

            return Pair(
                first = ModelExchangeMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    modelType = modelType.toString(Charsets.UTF_8),
                    model = Json.decodeFromString(model.toString(Charsets.UTF_8))
                ), second = localOffset
            )
        }
    }
}
