package com.example.federated_ml.ipv8

import com.example.federated_ml.models.OnlineModel
import com.frostwire.jlibtorrent.Sha1Hash
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
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

    @Test
    fun modelExchangeStoresCorrectly() = runBlockingTest {
        val crawler = TrustChainCrawler()
        val community = spyk(getCommunity())
        crawler.trustChainCommunity = community

        val newKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(newKey)

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)

        val model = OnlineModel(20)
        val count = community.performRemoteModelExchange(model, model::class::simpleName.toString(), 2u, newKey.keyToBin())
        Assert.assertEquals(1, count)

        // the exchanged model should be in the database now
        Assert.assertEquals(1, community.database.getAllBlocks().size)
    }

    @Test
    fun communicateReleaseBlocks() {
        val community = spyk(getCommunity())
        val model = OnlineModel(20)

        // 0 peers: we have 1 block, but no one to send it to
        Assert.assertEquals(0, community.communicateOnlineModels(model))

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)
        Assert.assertEquals(1, community.communicateOnlineModels(model))
    }
}
