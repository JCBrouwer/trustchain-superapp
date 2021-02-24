package com.example.federated_ml.ipv8

import com.example.federated_ml.models.OnlineModel
import nl.tudelft.ipv8.messaging.*

/**
 * This is a message from a peer asking for music content from other peers, filtered on a specific keyword
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
            serializeVarLen(model.serialize()) // TODO how serialize?
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
            // val (keyword, keywordSize) = deserializeVarLen(buffer, offset + localOffset)
            // localOffset += keywordSize
            return Pair(
                ModelExchangeMessage(
                    originPublicKey,
                    ttl,
                    modelType.toString(Charsets.US_ASCII),
                    model
                ), localOffset
            )
        }
    }
}
