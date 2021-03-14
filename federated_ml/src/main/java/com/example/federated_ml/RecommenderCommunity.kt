package com.example.federated_ml

import android.util.Log
import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.models.OnlineModel
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import kotlin.random.Random

@ExperimentalUnsignedTypes
open class RecommenderCommunity : TrustChainCommunity {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    val recommendStore: RecommenderStore

    class Factory(
        private val recommendStore: RecommenderStore,
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<RecommenderCommunity>(RecommenderCommunity::class.java) {
        override fun create(): RecommenderCommunity {
            return RecommenderCommunity(recommendStore, settings, database, crawler)
        }
    }

    constructor(recommendStore: RecommenderStore, settings: TrustChainSettings, database: TrustChainStore,
                crawler: TrustChainCrawler = TrustChainCrawler()): super(settings, database, crawler) {
        this.recommendStore = recommendStore
    }

    init {
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::onModelExchange
    }

    override fun load() {
        super.load()
        // TODO: adjust how many models we want to be initiated
        if (Random.nextInt(0, 1) == 0) {
            initiateWalkingModel()
        }
    }

    private fun initiateWalkingModel() {
        try {
            Log.i("Recommender", "Initiate random walk")
            performRemoteModelExchange(model = recommendStore.getLocalModel())
        } catch (e: Exception) {
            Log.i("Recommender", "Random walk failed")
            e.printStackTrace()
        }
    }

    @ExperimentalUnsignedTypes
    fun performRemoteModelExchange(
        model: OnlineModel,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        Log.i("Recommender", "My key is $originPublicKey")
        val maxPeersToAsk = 1
        var count = 0
        // TODO: delete myself from list
        for ((index, peer) in (getPeers() + myPeer).withIndex()) {
            if (index >= maxPeersToAsk) break
            val packet = serializePacket(
                MessageId.MODEL_EXCHANGE_MESSAGE,
                ModelExchangeMessage(originPublicKey, ttl, model.name, model)
            )
            send(peer, packet)
            count += 1
        }
        Log.i("Recommender", "Model exchanged with $count peer(s)")
        return count
    }

    @ExperimentalUnsignedTypes
    fun onModelExchange(packet: Packet) {
        Log.i("Recommender", "Some packet with model received")
        System.out.println(packet.toString())
        val (_, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model
        Log.i("Recommender", "Walking model is de-packaged")

        val localModel = recommendStore.getLocalModel().cast(modelType)

        val data = recommendStore.getLocalSongData()
        val songFeatures = data.first
        val playcounts = data.second
        val models = this.createModelMU(localModel, peerModel, songFeatures, playcounts)
        Log.i("Recommender", "Walking an random models are merged")

        this.recommendStore.storeModelLocally(models.first)

        performRemoteModelExchange(models.second)
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
