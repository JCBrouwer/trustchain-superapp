package com.example.federated_ml.ipv8

import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.ImplicitReflectionSerializer
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.sqldelight.Database

import org.junit.Assert
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecommenderCommunityTest {
    private fun createTrustChainStore(): TrustChainSQLiteStore {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        return TrustChainSQLiteStore(database)
    }

    @ImplicitReflectionSerializer
    private fun getCommunity(): RecommenderCommunity {
        val settings = TrustChainSettings()
        val store = createTrustChainStore()
        val community = RecommenderCommunity.Factory(settings = settings, database = store).create()
        val newKey = JavaCryptoProvider.generateKey()
        community.myPeer = Peer(newKey)
        community.endpoint = spyk(EndpointAggregator(mockk(relaxed = true), null))
        community.network = Network()
        community.maxPeers = 20
        return community
    }

    @ImplicitReflectionSerializer
    @Test
    fun modelStoresCorrectly() = runBlockingTest {
        val crawler = TrustChainCrawler()
        val community = spyk(getCommunity())
        crawler.trustChainCommunity = community

        val newKey1 = JavaCryptoProvider.generateKey()
        val myPeer = Peer(newKey1)

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)

        val model = Pegasos(0.01, 20, 10)
        val data = community.serializePacket(
            RecommenderCommunity.MessageId.MODEL_EXCHANGE_MESSAGE,
            ModelExchangeMessage(myPeer.publicKey.keyToBin(), 1u, model::class::simpleName.toString(), model)
        )
        val packet = Packet(neighborPeer.address, data)
        community.onModelExchange(packet)

        // the exchanged model should be in the database now
        Assert.assertEquals(1, community.database.getAllBlocks().size)
    }

    @ImplicitReflectionSerializer
    @Test
    fun communicateReleaseBlocks() {
        val community = spyk(getCommunity())
        val model = Pegasos(0.01, 20, 10)

        // 0 peers: we have 1 block, but no one to send it to
        Assert.assertEquals(0, community.performRemoteModelExchange(model,
            model::class::simpleName.toString()))

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)
        Assert.assertEquals(1, community.performRemoteModelExchange(model,
            model::class::simpleName.toString()))
    }
}
