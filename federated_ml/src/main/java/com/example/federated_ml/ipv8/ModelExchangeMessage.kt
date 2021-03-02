package com.example.federated_ml.ipv8

import kotlin.UInt
import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.parse
import nl.tudelft.ipv8.messaging.*

const val SERIALIZED_UINT_SIZE = 4
const val SERIALIZED_PUBLIC_KEY_SIZE = 74
/**
 * This is a message from a peer sending and asking for a model from other peers
 */
open class ModelExchangeMessage(
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
    companion object Deserializer: Deserializable<ModelExchangeMessage> {
        @ImplicitReflectionSerializer
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
            val (modelType, modelTypeSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelTypeSize
            val (model, modelSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelSize
            return Pair(
                first = ModelExchangeMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    modelType = modelType.toString(Charsets.US_ASCII),
                    model = Json.parse(model.toString(Charsets.US_ASCII))
                ), second = localOffset
            )
        }
    }
}
