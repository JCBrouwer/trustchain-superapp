package com.example.federated_ml

import android.util.Log
import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.models.OnlineModel
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.messaging.Packet
import java.util.*

@ExperimentalUnsignedTypes
open class RecommenderCommunity(
    val recommendStore: RecommenderStore
) : Community() {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"

    class Factory(
        private val recommendStore: RecommenderStore
    ) : Overlay.Factory<RecommenderCommunity>(RecommenderCommunity::class.java) {
        override fun create(): RecommenderCommunity {
            return RecommenderCommunity(recommendStore)
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
    fun onModelExchange(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model

        val localModel = recommendStore.getLocalModel().cast(modelType)

        val data = recommendStore.getSongData()
        val songFeatures = data.first
        val playcounts = data.second
        val models = this.createModelMU(localModel, peerModel, songFeatures, playcounts)

        recommendStore.storeModelLocally(models.first)

        performRemoteModelExchange(models.second, "Pegasos")

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
