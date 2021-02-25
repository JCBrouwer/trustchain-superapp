package com.example.federated_ml.ipv8

import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.messaging.Serializable

/**
 * This is a message from a peer sending and asking for a model from other peers
 */
class ModelExchangeMessage(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val modelType: String,
    val model: OnlineModel
) : Serializable {

    override fun serialize(): ByteArray {
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(modelType.toByteArray(Charsets.US_ASCII)) +
            serializeVarLen(model.serialize().toByteArray(Charsets.US_ASCII))
    }

    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    companion object Deserializer : Deserializable<ModelExchangeMessage> {  // TODO how deserialize?
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<ModelExchangeMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val (modelType, modelTypeSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelTypeSize
            val (model, modelSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelSize
            return Pair(
                ModelExchangeMessage(
                    originPublicKey,
                    ttl,
                    modelType.toString(Charsets.US_ASCII),
                    Json.decodeFromString(model.toString(Charsets.US_ASCII))
                ), localOffset
            )
        }
    }
}
