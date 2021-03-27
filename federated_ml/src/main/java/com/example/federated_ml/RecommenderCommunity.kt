package com.example.federated_ml

import android.util.Log
import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.ipv8.RequestModelMessage
import com.example.federated_ml.models.*
import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
import com.example.federated_ml.models.collaborative_filtering.PublicMatrixFactorization
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
        messageHandlers[MessageId.REQUEST_MODEL] = ::onModelRequest
    }

    override fun load() {
        super.load()

        // TODO: adjust how many models we want to be initiated
        if (Random.nextInt(0, 1) == 0) initiateWalkingModel()
    }

    fun initiateWalkingModel() {
        try {
            Log.w("Recommender", "Initiate random walk")
            performRemoteModelExchange(recommendStore.getLocalModel("Pegasos"))
            performRemoteModelExchange(recommendStore.getLocalModel("MatrixFactorization"))
        } catch (e: Exception) {
            Log.w("Recommender", "Random walk failed")
            e.printStackTrace()
        }
    }

    @ExperimentalUnsignedTypes
    fun performRemoteModelExchange(
        model: Model,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        Log.w("Recommender", "My key is $originPublicKey")
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
        Log.w("Recommender", "Model exchanged with $count peer(s)")
        return count
    }

    @ExperimentalUnsignedTypes
    fun onModelExchange(packet: Packet) {
        Log.w("Recommender", "Some packet with model received")
        val (_, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        var peerModel = payload.model
        Log.w("Recommender", "Walking model is de-packaged")

        var localModel = recommendStore.getLocalModel(modelType)

        if (modelType == "Adaline" || modelType == "Pegasos") {
            val data = recommendStore.getLocalSongData()
            val songFeatures = data.first
            val playcounts = data.second
            val models = createModelMU(localModel as OnlineModel, peerModel as OnlineModel, songFeatures, playcounts)
            Log.w("Recommender", "Walking an random models are merged")
            recommendStore.storeModelLocally(models.first)
            if (payload.checkTTL()) performRemoteModelExchange(models.second)
        } else {
            peerModel = peerModel as PublicMatrixFactorization
            localModel = localModel as MatrixFactorization
            if (localModel.songFeatures.size == 0) {
                localModel.updateRatings(recommendStore.getSongIds(), recommendStore.getPlaycounts())
                localModel = MatrixFactorization(peerModel.peerFeatures)
                recommendStore.storeModelLocally(localModel)
                val maxPeersToAsk = 10
                var count = 0
                for ((index, peer) in getPeers().withIndex()) {
                    if (index >= maxPeersToAsk) break
                    send(
                        peer,
                        serializePacket(
                            MessageId.REQUEST_MODEL,
                            RequestModelMessage(myPeer.publicKey.keyToBin(), 1u, "MatrixFactorization")
                        )
                    )
                    count += 1
                }
                Log.w("Recommender", "Model request sent to $count peer(s)")
            } else {
                Log.w("Recommender", "Merging MatrixFactorization")
                localModel.updateRatings(recommendStore.getSongIds(), recommendStore.getPlaycounts())
                localModel.merge(peerModel.peerFeatures)
                recommendStore.storeModelLocally(localModel)
                Log.w("Recommender", "Stored new MatrixFactorization")
                if (payload.checkTTL()) performRemoteModelExchange(localModel)
            }
        }
    }

//    private fun trainMatrixFactorization(model: MatrixFactorization) {
//        val maxPeersToAsk = 10
//
//        val numSongs = recommendStore.globalSongCount()
//        var ageGather = Array(numSongs) { _ -> 0.0 }
//        var songFeaturesGather = recommendStore.getSongIds().zip(Array(numSongs) { _ -> Array(model.k) { _ -> 0.0 } }).toMap().toSortedMap()
//        var songBiasGather = Array(numSongs) { _ -> 0.0 }
//
//        for ((index, peer) in getPeers().withIndex()) {
//            if (index >= maxPeersToAsk) break
//            send(
//                peer,
//                serializePacket(
//                    MessageId.REQUEST_MODEL,
//                    RequestModelMessage(myPeer.publicKey.keyToBin(), 3u, "MatrixFactorization")
//                )
//            )
//        }
//
//        // TODO how to receive models from peers all at once rather than 1-by-1 updates from the omModelExchange callback?
// //        for ((index, peer) in getPeers().withIndex()) {
// //            if (index >= maxPeersToAsk) break
// //            val (peerAge: Array<Double>, peerSongs: Array<Array<Double>>, peerSongBias: Array<Double>) = receive(peer)
// //            ageGather += peerAge
// //            songFeaturesGather += peerSongs
// //            songBiasGather += peerSongBias
// //        }
//
//        model.merge(ageGather, songFeaturesGather, songBiasGather)
//        model.update()
//        recommendStore.storeModelLocally(model)
//    }

    private fun onModelRequest(packet: Packet) {
        Log.w("Recommender", "Some packet with model received")
        val (_, payload) = packet.getAuthPayload(ModelExchangeMessage)
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val model = recommendStore.getLocalModel(modelType)
        send(
            packet.source,
            serializePacket(
                MessageId.MODEL_EXCHANGE_MESSAGE,
                ModelExchangeMessage(myPeer.publicKey.keyToBin(), 1u, model.name, model)
            )
        )
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
        val REQUEST_MODEL: Int
            get() { return 40 }
    }
}
