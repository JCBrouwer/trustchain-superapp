package com.example.federated_ml

import android.util.Log
import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.div
import com.example.federated_ml.models.plus
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import java.util.*
import kotlin.random.Random

@ExperimentalUnsignedTypes
open class RecommenderCommunity(
    val recommendStore: RecommenderStore,
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"

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

    init {
        messageHandlers[MessageId.MODEL_EXCHANGE_MESSAGE] = ::onModelExchange
        messageHandlers[MessageId.REQUEST_COLAB_FILTER] = ::onColabFilterRequest
        messageHandlers[MessageId.RESPOND_COLAB_FILTER] = ::onColabFilterResponse
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
        val maxPeersToAsk = 5
        var count = 0
        for ((index, peer) in getPeers().withIndex()) {
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
//        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model
        Log.i("Recommender", "Walking model is de-packaged")

        val localModel = recommendStore.getLocalModel().cast()

        val data = recommendStore.getLocalSongData()
        val songFeatures = data.first
        val playcounts = data.second
        val models = createModelMU(localModel, peerModel, songFeatures, playcounts)
        Log.i("Recommender", "Walking an random models are merged")

        recommendStore.storeModelLocally(models.first)

        performRemoteModelExchange(models.second)
    }

    fun trainColabFilter(   ) {
        val model = recommendStore.getColabFilter()

        if (model == null) {
            val maxPeersToAsk = 5
            var count = 0
            for ((index, peer) in getPeers().withIndex()) {
                if (index >= maxPeersToAsk) break
                val packet = serializePacket(
                    MessageId.REQUEST_COLAB_FILTER,
                    RequestColabFilterMessage(myPeer.publicKey.keyToBin(), 1u)
                )
                send(peer, packet)
                count += 1
            }
            Log.i("Recommender", "Model exchanged with $count peer(s)")
        }

        var ageGather = Array(numSongs) { _ -> 0.0 }
        var songFeaturesGather = Array(numSongs) { _ -> Array(k) { _ -> 0.0 } }
        var songBiasGather = Array(numSongs) { _ -> 0.0 }
        for (node in community.getPeers()) {
            send(node, (age, songFeatures, songBias))
            val (peerAge, peerSongs, peerSongBias) = receive(node)
            ageGather += peerAge
            songFeaturesGather += peerSongs
            songBiasGather += peerSongBias
        }
        for (j in 1..numSongs) {
            if (ageGather[j] != 0.0) {
                songFeatures[j] = songFeatures[j] + songFeaturesGather[j] / ageGather[j]
                songBias[j] = songBias[j] + songBiasGather[j] / ageGather[j]
                age[j] += 1.0
            }
        }
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
            get() { return 27 }
        val REQUEST_COLAB_FILTER: Int
            get() { return 40 }
        val RESPOND_COLAB_FILTER: Int
            get() { return 42 }
    }
}
