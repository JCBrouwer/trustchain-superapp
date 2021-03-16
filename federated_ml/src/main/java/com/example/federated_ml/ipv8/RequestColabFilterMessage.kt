package com.example.federated_ml.ipv8

import android.util.Log
import kotlin.UInt
import kotlinx.serialization.json.*
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.messaging.*

/**
 * This is a message from a peer sending and asking for a model from other peers
 */
open class RequestColabFilterMessage @ExperimentalUnsignedTypes constructor(
    val originPublicKey: ByteArray,
    var ttl: UInt
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

    companion object Deserializer : Deserializable<RequestColabFilterMessage> {
        @ExperimentalUnsignedTypes
        @JvmStatic
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RequestColabFilterMessage, Int> {
            var localOffset = 0
            val originPublicKey = buffer.copyOfRange(
                offset + localOffset,
                offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
            )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)
            return Pair(
                first = RequestColabFilterMessage(
                    originPublicKey = originPublicKey,
                    ttl = ttl
                ), second = localOffset
            )
        }
    }
}
