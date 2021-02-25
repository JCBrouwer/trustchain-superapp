package com.example.federated_ml.ipv8

import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.parse
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

    @ImplicitReflectionSerializer
    override fun serialize(): ByteArray {
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(modelType.toByteArray(Charsets.US_ASCII)) +
            serializeVarLen(model.serialize().toByteArray(Charsets.US_ASCII))
    }

    @ImplicitReflectionSerializer
    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    @ImplicitReflectionSerializer
    companion object Deserializer : Deserializable<ModelExchangeMessage> {
        @OptIn(UnstableDefault::class)
        @ImplicitReflectionSerializer
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<ModelExchangeMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = nl.tudelft.ipv8.messaging.deserializeUInt(buffer, offset + localOffset) // TODO java.lang.NoSuchMethodError ?!
            localOffset += SERIALIZED_UINT_SIZE
            val (modelType, modelTypeSize) = nl.tudelft.ipv8.messaging.deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelTypeSize
            val (model, modelSize) = nl.tudelft.ipv8.messaging.deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelSize
            return Pair(
                first = ModelExchangeMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    modelType = modelType.toString(Charsets.US_ASCII),
                    model = Json.parse<OnlineModel>(model.toString(Charsets.US_ASCII))
                ), second = localOffset
            )
        }
    }
}
