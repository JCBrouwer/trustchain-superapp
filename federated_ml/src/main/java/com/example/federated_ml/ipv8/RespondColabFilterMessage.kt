package com.example.federated_ml.ipv8

import android.util.Log
import com.example.federated_ml.models.MatrixFactorization
import kotlin.UInt
import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.messaging.*

/**
 * This is a message from a peer sending and asking for a model from other peers
 */
open class RespondColabFilterMessage @ExperimentalUnsignedTypes constructor(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val model: MatrixFactorization
) : Serializable {

    override fun serialize(): ByteArray {
        return originPublicKey + serializeUInt(ttl)
    }

    @kotlin.ExperimentalUnsignedTypes
    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    companion object Deserializer : Deserializable<RespondColabFilterMessage> {
        @ExperimentalUnsignedTypes
        @JvmStatic
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RespondColabFilterMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)

            val (model, modelSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += modelSize

            return Pair(
                first = RespondColabFilterMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl,
                    model = Json.decodeFromString(model.toString(Charsets.UTF_8))
                ), second = localOffset
            )
        }
    }
}
