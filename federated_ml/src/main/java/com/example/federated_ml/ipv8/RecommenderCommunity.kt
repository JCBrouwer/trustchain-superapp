package com.example.federated_ml.ipv8

import android.util.Log
import com.frostwire.jlibtorrent.Sha1Hash
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import kotlin.random.Random

import com.example.federated_ml.models.WeakLearner

class RecommenderCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>()
    val defaultModelType = "WeakLearner"

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
        model: WeakLearner,
        modelType: String = "WeakLearner",
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin(),
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
        val peerFeatures = payload.model // how is this serialized?

        // update local model with it and respond
        // something like this?
        val localFeatures = database.getBlocksWithType(modelType)
        if (localFeatures != null) {
            localModel.updateWithNewModel(peerFeatures)
        }
        else {
            if (!payload.checkTTL()) return
            performRemoteModelExchange(peerFeatures, modelType, payload.ttl, payload.originPublicKey)
        }

        Log.i("ModelExchange from", peer.mid)
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
     * Communicate local model to one or a few random peers
     * @return the amount of blocks that were sent
     */
    fun broadcastModel(): Int {
        val peer = pickRandomPeer() ?: return 0
        val releaseBlocks = database.getBlocksWithType(modelType)
        val maxBlocks = 3
        var count = 0
        releaseBlocks.shuffled().withIndex().forEach {
            count += 1
            if (it.index >= maxBlocks) return count
            sendBlock(it.value, peer)
        }
        return count
    }

    object MessageId {
        const val MODEL_EXCHANGE_MESSAGE = 10
    }
}
