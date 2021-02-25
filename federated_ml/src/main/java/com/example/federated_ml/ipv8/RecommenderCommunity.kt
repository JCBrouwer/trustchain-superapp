package com.example.federated_ml.ipv8

import com.example.federated_ml.models.OnlineModel
import com.google.android.exoplayer2.util.Log
import com.google.common.math.DoubleMath.roundToInt
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import kotlin.random.Random

import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore

@ImplicitReflectionSerializer
class RecommenderCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<RecommenderCommunity>(RecommenderCommunity::class.java) {
        override fun create(): RecommenderCommunity {
            return RecommenderCommunity(settings, database, crawler)
        }
    }

    init {
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::onModelExchange
    }

    fun performRemoteModelExchange(
        model: OnlineModel,
        modelType: String,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        val maxPeersToAsk = 20 // This is a magic number, tweak during/after experiments
        var count = 0
        for ((index, peer) in getPeers().withIndex()) {
            if (index >= maxPeersToAsk) break
            val packet = serializePacket(
                MessageId.MODEL_EXCHANGE_MESSAGE,
                ModelExchangeMessage(originPublicKey, ttl, modelType, model)
            )
            send(peer, packet)
            count += 1
        }
        return count
    }

    @OptIn(UnstableDefault::class)
    @ImplicitReflectionSerializer
    private fun onModelExchange(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model

        val localModel = Json.parse<OnlineModel>(
            database.getBlocksWithType(modelType).get(0).toString()
        )

        // TODO We should get the song history from Esmee's local database rather than trustchain,
        //  otherwise we're just getting the 1000 songs we've most recently received from peers
        val myKey = this.myPeer.publicKey.keyToBin()
        val songsHistory = database.getLatestBlocks(myKey, 1000)
        val labelVector = Array<String>(songsHistory.size) { _ -> "" }
        for ((i, block) in songsHistory.withIndex()) {
            labelVector[i] = block.blockId
        }
        val processedSongHistory = processSongs(labelVector)

        val models = this.createModelMU(localModel, peerModel, processedSongHistory.first,
            processedSongHistory.second)

        val modelBlock = TrustChainBlock(
            models.first::class::simpleName.toString(),
            models.first.serialize().toByteArray(Charsets.US_ASCII),
            myKey,
            1u,
            ANY_COUNTERPARTY_PK,
            0u,
            GENESIS_HASH,
            EMPTY_SIG,
            Date()
        )
        database.addBlock(modelBlock)

        communicateOnlineModels(models.second)

        Log.i("ModelExchange from", peer.mid)
    }

    fun processSongs(songsList: Array<String>): Pair<Array<Array<Double>>, IntArray> {
        // TODO "How can we distinguish songs by their id and not different blocks with the same song?
        return Pair(Array(10, { a -> Array(20, { b -> b * 0.25 + a }) }), IntArray(10, { a -> roundToInt(a * 0.5, java.math.RoundingMode.FLOOR) }))
    }

    fun createModelRW(
        incomingModel: OnlineModel,
        localModel: OnlineModel,
        features: Array<Array<Double>>,
        labels: IntArray
    ):
        Pair<OnlineModel, OnlineModel> {
        incomingModel.update(features, labels)
        return Pair(localModel, incomingModel)
    }

    fun createModelUM(
        incomingModel: OnlineModel,
        localModel: OnlineModel,
        features: Array<Array<Double>>,
        labels: IntArray
    ):
        Pair<OnlineModel, OnlineModel> {
        incomingModel.update(features, labels)
        localModel.merge(incomingModel)
        return Pair(localModel, incomingModel)
    }

    fun createModelMU(
        incomingModel: OnlineModel,
        localModel: OnlineModel,
        features: Array<Array<Double>>,
        labels: IntArray
    ):
        Pair<OnlineModel, OnlineModel> {
        localModel.merge(incomingModel)
        incomingModel.update(features, labels)
        return Pair(localModel, incomingModel)
    }

    private fun pickRandomPeer(): Peer? {
        val peers = getPeers()
        if (peers.isEmpty()) return null
        var index = 0
        // Pick a random peer if we have more than 1 connected
        if (peers.size > 1) {
            index = Random.nextInt(peers.size - 1)
        }
        return peers[index]
    }

    /**
     * Communicate an existing online model
     */
    fun communicateOnlineModels(peerModel: OnlineModel): Int {
        val peer = pickRandomPeer() ?: return 0
        val releaseBlock = TrustChainBlock(
            peerModel::class::simpleName.toString(),
            peerModel.serialize().toByteArray(Charsets.US_ASCII),
            this.myPeer.publicKey.keyToBin(),
            GENESIS_SEQ,
            ANY_COUNTERPARTY_PK,
            UNKNOWN_SEQ,
            GENESIS_HASH,
            EMPTY_SIG,
            Date(0)
        )
        sendBlock(releaseBlock, peer)
        return 1
    }

    object MessageId {
        val MODEL_EXCHANGE_MESSAGE: Int
            get() {
                return 27
            }
    }
}
