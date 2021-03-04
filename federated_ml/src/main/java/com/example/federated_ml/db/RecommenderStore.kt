package com.example.federated_ml.db

import com.example.federated_ml.models.OnlineModel
import com.google.common.math.DoubleMath
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import nl.tudelft.ipv8.attestation.trustchain.ANY_COUNTERPARTY_PK
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_SIG
import nl.tudelft.ipv8.attestation.trustchain.GENESIS_HASH
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.math.RoundingMode
import java.util.*

class RecommenderStore(private val recommendStore: TrustChainSQLiteStore, private val musicStore: TrustChainSQLiteStore, private val key: ByteArray) {

    @kotlinx.serialization.UnstableDefault
    @ImplicitReflectionSerializer
    @kotlin.ExperimentalUnsignedTypes
    fun storeModel(model: OnlineModel) {
        val modelBlock = TrustChainBlock(
            model::class::simpleName.toString(),
            model.serialize().toByteArray(Charsets.US_ASCII),
            key,
            1u,
            ANY_COUNTERPARTY_PK,
            0u,
            GENESIS_HASH,
            EMPTY_SIG,
            Date()
        )
        recommendStore.addBlock(modelBlock)
    }

    @ImplicitReflectionSerializer
    @kotlinx.serialization.UnstableDefault
    fun getLocalModel(modelType: String) : OnlineModel {
        return Json.parse(recommendStore.getBlocksWithType(modelType)[0].toString())
    }

    private fun processSongs(): Pair<Array<Array<Double>>, IntArray> {
        // TODO "How can we distinguish songs by their id and not different blocks with the same song?
        return Pair(Array(10) { a -> Array(20) { b -> b * 0.25 + a } },
            IntArray(10) { a -> DoubleMath.roundToInt(a * 0.5, RoundingMode.FLOOR) })
    }

    fun getSongFeatures(limit: Int = 1000 ) : Array<Array<Double>> {
        val songsHistory = musicStore.getLatestBlocks(key, limit)
        val labelVector = Array<String>(songsHistory.size) { _ -> "" }
        for ((i, block) in songsHistory.withIndex()) {
            labelVector[i] = block.blockId
        }
        return processSongs().first
    }


    fun getPlayCounts() : IntArray {
        return processSongs().second
    }

}
