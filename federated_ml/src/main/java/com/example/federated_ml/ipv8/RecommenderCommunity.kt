package com.example.federated_ml.ipv8

import android.util.Log
import com.frostwire.jlibtorrent.Sha1Hash
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import kotlin.random.Random
import kotlinx.serialization.*
import kotlinx.serialization.json.*

import com.example.federated_ml.models.OnlineModel
import nl.tudelft.ipv8.attestation.trustchain.*

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
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::communicateOnlineModels
    }

    fun performRemoteModelExchange(
        model: OnlineModel,
        modelType: String = ,
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

    private fun onModelExchange(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        var peerModel = payload.model // how is this serialized?

        // we need to deserialize model somehow
        val localModel = Json.decodeFromString<OnlineModel>(database.getBlocksWithType(modelType).get(0)
            .toString())

        // TODO: how to get own peer key?
        val myKey = trustChainCommunity.myPeer.publicKey.keyToBin()
        val songsHistory = database.getLatestBlocks(myKey, 1000)
        val labelVector = Array<String>(songsHistory.size){_ -> ""}
        for ((i, block) in songsHistory.withIndex()){
            labelVector[i] = block.blockId
        }
        var processedSongHistory = processSongs(labelVector)

        val models = this.createModelMU(localModel, peerModel, processedSongHistory.first,
            processedSongHistory.second)

        models.first.store(myKey)
        communicateOnlineModels(models.second, myKey)

        Log.i("ModelExchange from", peer.mid)
    }

    fun processSongs(songsList: Array<String>): Pair<Array<Array<Double>>, IntArray>{
        TODO("How can we distinguish songs by their id and " +
            "not different blocks with the same song?")
    }

    fun createModelRW(incomingModel: OnlineModel, localModel: OnlineModel,
                      features: Array<Array<Double>>, labels: IntArray):
        Pair<OnlineModel, OnlineModel> {
        incomingModel.update(features, labels)
        return Pair(localModel, incomingModel)
    }

    fun createModelUM(incomingModel: OnlineModel, localModel: OnlineModel,
                      features: Array<Array<Double>>, labels: IntArray):
        Pair<OnlineModel, OnlineModel> {
        incomingModel.update(features, labels)
        localModel.merge(incomingModel)
        return Pair(localModel, incomingModel)
    }

    fun createModelMU(incomingModel: OnlineModel, localModel: OnlineModel,
                      features: Array<Array<Double>>, labels: IntArray):
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
    fun communicateOnlineModels(peerModel: OnlineModel, myKey: ByteArray) {
        val peer = pickRandomPeer() ?: return 0
        val releaseBlock = TrustChainBlock(
            peerModel::class::simpleName.toString(),
            peerModel.serialize().toByteArray(Charsets.US_ASCII),
            myKey,
            GENESIS_SEQ,
            ANY_COUNTERPARTY_PK,
            UNKNOWN_SEQ,
            GENESIS_HASH,
            EMPTY_SIG,
            Date(0)
        )
        sendBlock(releaseBlock, peer)
    }

    object MessageId {
        val MODEL_EXCHANGE_MESSAGE: Int
            get() {
                TODO()
            }
    }
}
