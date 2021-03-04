package com.example.federated_ml

import android.util.Log
import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import kotlinx.serialization.ImplicitReflectionSerializer
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File

@kotlinx.serialization.UnstableDefault
@ExperimentalUnsignedTypes
@ImplicitReflectionSerializer
open class RecommenderCommunity(
    settings: TrustChainSettings,
    recommendStore: TrustChainSQLiteStore,
    musicStore: TrustChainSQLiteStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, recommendStore, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    // TODO: chnage it to a proper directory
    private val dummyDir = File("mySongs")
    private val store = RecommenderStore(recommendStore, musicStore,
        this.myPeer.publicKey.keyToBin(), dummyDir)

    class Factory(
        private val settings: TrustChainSettings,
        private val recommendStore: TrustChainSQLiteStore,
        private val musicStore: TrustChainSQLiteStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<RecommenderCommunity>(RecommenderCommunity::class.java) {
        override fun create(): RecommenderCommunity {
            return RecommenderCommunity(settings, recommendStore, musicStore, crawler)
        }
    }

    init {
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::onModelExchange
    }

    @ExperimentalUnsignedTypes
    fun performRemoteModelExchange(
        model: OnlineModel,
        modelType: String,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        val maxPeersToAsk = 1 // This is a magic number, tweak during/after experiments
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

    @ExperimentalUnsignedTypes
    @kotlinx.serialization.UnstableDefault
    @ImplicitReflectionSerializer
    fun onModelExchange(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model

        var localModel = Pegasos(0.01, 20, 10)

        try {
            localModel = store.getLocalModel(modelType) as Pegasos
        } catch (e: Exception){
            Log.i("Error: ", e.toString())
            Log.i("Created model for peer ", peer.mid)
        }

        val data = store.getData()
        val songFeatures = data.first
        val playcounts = data.second
        val models = this.createModelMU(localModel, peerModel, songFeatures, playcounts)

        store.storeModel(models.first)

        performRemoteModelExchange(models.second, models.second::class::simpleName.toString())

        Log.i("ModelExchange from", peer.mid)
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

    object MessageId {
        val MODEL_EXCHANGE_MESSAGE: Int
            get() {
                return 27
            }
    }
}
