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

import com.example.federated_ml.WeakLearner
import com.example.federated_ml.models.OnlineModel

class RecommenderCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    // has been received from peers
    var onlineModelExists = false

    // general model in community
    private var onlineModel: OnlineModel?

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
        this.onlineModel = null
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::communicateOnlineModels
    }

    fun performRemoteModelExchange(
        model: WeakLearner,
        modelType: String = "WeakLearner",
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
        val peerFeatures = payload.model // how is this serialized?

        // update local model with it and respond
        // something like this?

        database.dbModelQueries.addModel()

        val localFeatures = database.getBlocksWithType(modelType)
        localModel.updateWithNewModel(peerFeatures)

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
     * Communicate an existing online model
     */
    fun communicateOnlineModels(): Int {
        // might want to add more tracking lately to where the model can be
        if (!this.onlineModelExists){
            this.initiateOnlineModels()
            this.onlineModelExists = true
        }
        return 1
        TODO("Not yet implemented")
    }

    /**
     * Initiate online model
     */
    private fun initiateOnlineModels(){
        val peer = pickRandomPeer() ?: return
        // TODO: here we need to take ALL SONGS from a database
        // val allSongs = database.getAllSongs("publish_release")

        // this creates model for all songs as features
        this.onlineModel = OnlineModel(10)
        val maxModels = 1
        for(i in 0..maxModels){
            sendModel(onlineModel!!, peer)
        }
        TODO("Not yet implemented")
    }

    object MessageId {
        val MODEL_EXCHANGE_MESSAGE: Int
            get() {
                TODO()
            }
    }
}
