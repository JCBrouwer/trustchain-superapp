package com.example.federated_ml

import com.example.federated_ml.db.RecommenderStore
import com.example.federated_ml.ipv8.ModelExchangeMessage
import com.example.federated_ml.models.Pegasos
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.federated_ml.sqldelight.Database as MLDatabase
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver

import org.junit.Assert
import org.junit.Test
import java.util.*

class RecommenderCommunityTest {
    private fun createTrustChainStore(): MLDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = MLDatabase(driver)
        return database
    }

    @ExperimentalUnsignedTypes
    private fun getCommunity(): RecommenderCommunity {
        val musicDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val musicStore = TrustChainSQLiteStore(Database(musicDriver))
        val recommendStore = RecommenderStore.getInstance(musicStore, createTrustChainStore())

        val community = RecommenderCommunity.Factory(recommendStore = recommendStore).create()
        val newKey = JavaCryptoProvider.generateKey()
        community.myPeer = Peer(newKey)
        community.recommendStore.key = newKey.keyToBin()
        community.endpoint = spyk(EndpointAggregator(mockk(relaxed = true), null))
        community.network = Network()
        community.maxPeers = 20
        return community
    }

    @ExperimentalUnsignedTypes
    @Test
    fun deserializedModelStoresCorrectly() {
        val community = spyk(getCommunity())

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)

        val model = Pegasos(0.01, 20, 10)
        val data = community.serializePacket(
            RecommenderCommunity.MessageId.MODEL_EXCHANGE_MESSAGE,
            ModelExchangeMessage(community.myPeer.publicKey.keyToBin(), 1u,
                model::class::simpleName.toString(), model)
        )
        val packet = Packet(neighborPeer.address, data)

        val (_, payload) = packet.getAuthPayload(ModelExchangeMessage)
        val peerModel = payload.model
        community.recommendStore.storeModelLocally(peerModel)
        val localModel = community.recommendStore.getLocalModel()
        Assert.assertEquals(peerModel.weights, localModel.weights)
    }
}
