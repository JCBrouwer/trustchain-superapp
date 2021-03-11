package nl.tudelft.trustchain.federated_ml.ipv8

import nl.tudelft.trustchain.federated_ml.RecommenderCommunity
import nl.tudelft.trustchain.federated_ml.models.Pegasos
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
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

import org.junit.Assert
import org.junit.Test
import java.util.*

class RecommenderCommunityTest {
    private fun createTrustChainStore(): TrustChainSQLiteStore {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        return TrustChainSQLiteStore(database)
    }

    @ExperimentalUnsignedTypes
    private fun getCommunity(): RecommenderCommunity {
        val settings = TrustChainSettings()
        val recommendStore = createTrustChainStore()
        val musicStore = createTrustChainStore()
        val community = RecommenderCommunity.Factory(settings = settings, recommendStore = recommendStore, musicStore = musicStore).create()
        val newKey = JavaCryptoProvider.generateKey()
        community.myPeer = Peer(newKey)
        community.store.key = newKey.keyToBin()
        community.endpoint = spyk(EndpointAggregator(mockk(relaxed = true), null))
        community.network = Network()
        community.maxPeers = 20
        return community
    }

    @ExperimentalUnsignedTypes
    @Test
    fun deserializedModelStoresCorrectly() {
        val crawler = TrustChainCrawler()
        val community = spyk(getCommunity())
        crawler.trustChainCommunity = community

        // create some dummy in the store
        community.store.storeSong(mapOf(
            "magnet" to "magnet1",
            "title" to "title1",
            "artists" to "artists1",
            "date" to "date1",
            "torrentInfoName" to "torrentInfoName1"
        ))

        // 1 peer: send to 1 person, because we have 1 block
        val newKey2 = JavaCryptoProvider.generateKey()
        val neighborPeer = Peer(newKey2)
        every {
            community.getPeers()
        } returns listOf(neighborPeer)

        val model = Pegasos(0.01, 20, 10)
        val data = community.serializePacket(
            RecommenderCommunity.MessageId.MODEL_EXCHANGE_MESSAGE,
            ModelExchangeMessage(community.myPeer.publicKey.keyToBin(), 1u, model::class::simpleName.toString(), model)
        )
        val packet = Packet(neighborPeer.address, data)
//        community.onModelExchange(packet) // TODO NOTE the code below is copied from onModelExchange

        val (_, payload) = packet.getAuthPayload(ModelExchangeMessage)

        // packet contains model type and weights from peer
//        val modelType = payload.modelType.toLowerCase(Locale.ROOT)
        val peerModel = payload.model
        community.store.storeModel(peerModel)

//        var localModel = Pegasos(0.01, 20, 10)
//        try {
//            localModel = community.store.getLocalModel(modelType) as Pegasos
//        } catch (e: Exception){
//            Log.i("Error: ", e.toString())
//            Log.i("Created model for peer ", peer.mid)
//        }
//        val dat = community.store.getData()
//        val songFeatures = dat.first
//        val playcounts = dat.second
//        val models = community.createModelMU(localModel, peerModel, songFeatures, playcounts) // TODO null pointer error here. android mock issues again?

//        community.store.storeModel(models.first)

        // the exchanged model should be in the database now
        Assert.assertEquals(1, community.database.getAllBlocks().size)
    }

    @ExperimentalUnsignedTypes
    @Test
    fun communicateModelBlocks() {
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
