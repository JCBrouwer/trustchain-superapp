package com.example.federated_ml.ipv8

import kotlin.UInt
import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.parse
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.serializeUInt
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.ipv8.messaging.Serializable
import unsigned.Ubyte
import unsigned.Uint

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
        @OptIn(UnstableDefault::class)
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

        fun deserializeVarLen(buffer: ByteArray, offset: Int = 0): Pair<ByteArray, Int> {
            val len = deserializeUInt(buffer, offset).toInt()
            val payload = buffer.copyOfRange(offset + SERIALIZED_UINT_SIZE,
                offset + SERIALIZED_UINT_SIZE + len)
            return Pair(payload, SERIALIZED_UINT_SIZE + len)
        }

        fun deserializeUInt(buffer: ByteArray, offset: Int = 0): UInt {
            val ubuffer = UbyteArray(buffer)
            var result = Uint(0)
            for (i in 0 until SERIALIZED_UINT_SIZE) {
                result = (result shl 8) or ubuffer[offset + i].toUint()
            }
            return result.toInt().toUInt()
        }

        class UbyteArray(val data: ByteArray) {

            operator fun get(index: Int) = Ubyte(data[index])

            operator fun set(index: Int, value: Ubyte) {
                data[index] = value.v
            }
        }
    }
}
