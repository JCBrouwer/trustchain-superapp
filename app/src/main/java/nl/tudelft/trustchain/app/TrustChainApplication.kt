package nl.tudelft.trustchain.app

import android.app.Application
import android.bluetooth.BluetoothManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.example.musicdao.ipv8.MusicCommunity
import com.example.federated_ml.RecommenderCommunity
import com.squareup.sqldelight.android.AndroidSqliteDriver
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.android.messaging.bluetooth.BluetoothLeDiscovery
import nl.tudelft.ipv8.android.peerdiscovery.NetworkServiceDiscovery
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.tftp.TFTPCommunity
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.app.service.TrustChainService
import nl.tudelft.trustchain.common.DemoCommunity
import nl.tudelft.trustchain.common.MarketCommunity
import nl.tudelft.trustchain.common.eurotoken.GatewayStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.currencyii.CoinCommunity
import nl.tudelft.trustchain.eurotoken.community.EuroTokenCommunity
import nl.tudelft.trustchain.peerchat.community.PeerChatCommunity
import nl.tudelft.trustchain.peerchat.db.PeerChatStore
import nl.tudelft.trustchain.voting.VotingCommunity

@ExperimentalUnsignedTypes
class TrustChainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        defaultCryptoProvider = AndroidCryptoProvider

        initIPv8()
    }

    private fun initIPv8() {
        val config = IPv8Configuration(
            overlays = listOf(
                createDiscoveryCommunity(),
                createTrustChainCommunity(),
                createPeerChatCommunity(),
                createEuroTokenCommunity(),
                createTFTPCommunity(),
                createDemoCommunity(),
                createMarketCommunity(),
                createCoinCommunity(),
                createVotingCommunity(),
                createMusicCommunity(),
                createRecommenderCommunity()
            ), walkerInterval = 5.0
        )

        IPv8Android.Factory(this)
            .setConfiguration(config)
            .setPrivateKey(getPrivateKey())
            .setServiceClass(TrustChainService::class.java)
            .init()

        initTrustChain()
    }

    private fun initTrustChain() {
        val ipv8 = IPv8Android.getInstance()
        val trustchain = ipv8.getOverlay<TrustChainCommunity>()!!

        val tr = TransactionRepository(trustchain, GatewayStore.getInstance(this))
        tr.initTrustChainCommunity() // register eurotoken listners
        val euroTokenCommunity = ipv8.getOverlay<EuroTokenCommunity>()!!
        euroTokenCommunity.setTransactionRepository(tr)

        trustchain.registerTransactionValidator(BLOCK_TYPE, object : TransactionValidator {
            override fun validate(
                block: TrustChainBlock,
                database: TrustChainStore
            ): ValidationResult {
                if (block.transaction["message"] != null || block.isAgreement) {
                    return ValidationResult.Valid
                } else {
                    return ValidationResult.Invalid(listOf("Proposal must have a message"))
                }
            }
        })

        trustchain.registerBlockSigner(BLOCK_TYPE, object : BlockSigner {
            override fun onSignatureRequest(block: TrustChainBlock) {
                trustchain.createAgreementBlock(block, mapOf<Any?, Any?>())
            }
        })

        trustchain.addListener(BLOCK_TYPE, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("TrustChainDemo", "onBlockReceived: ${block.blockId} ${block.transaction}")
            }
        })

        trustchain.addListener(CoinCommunity.JOIN_BLOCK, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("Coin", "onBlockReceived: ${block.blockId} ${block.transaction}")
            }
        })

        trustchain.addListener(CoinCommunity.SIGNATURE_ASK_BLOCK, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("Coin", "onBlockReceived: ${block.blockId} ${block.transaction}")
            }
        })
    }

    private fun createDiscoveryCommunity(): OverlayConfiguration<DiscoveryCommunity> {
        val randomWalk = RandomWalk.Factory()
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()

        val nsd = NetworkServiceDiscovery.Factory(getSystemService()!!)
        val bluetoothManager = getSystemService<BluetoothManager>()
            ?: throw IllegalStateException("BluetoothManager not available")
        val strategies = mutableListOf(
            randomWalk, randomChurn, periodicSimilarity, nsd
        )
        if (bluetoothManager.adapter != null && Build.VERSION.SDK_INT >= 24) {
            val ble = BluetoothLeDiscovery.Factory()
            strategies += ble
        }

        return OverlayConfiguration(
            DiscoveryCommunity.Factory(),
            strategies
        )
    }

    private fun createTrustChainCommunity(): OverlayConfiguration<TrustChainCommunity> {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "trustchain.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    private fun createEuroTokenCommunity(): OverlayConfiguration<EuroTokenCommunity> {
        val randomWalk = RandomWalk.Factory()
        val store = GatewayStore.getInstance(this)
        return OverlayConfiguration(
            EuroTokenCommunity.Factory(this, store),
            listOf(randomWalk)
        )
    }

    private fun createPeerChatCommunity(): OverlayConfiguration<PeerChatCommunity> {
        val randomWalk = RandomWalk.Factory()
        val store = PeerChatStore.getInstance(this)
        return OverlayConfiguration(
            PeerChatCommunity.Factory(store, this),
            listOf(randomWalk)
        )
    }

    private fun createTFTPCommunity(): OverlayConfiguration<TFTPCommunity> {
        return OverlayConfiguration(
            Overlay.Factory(TFTPCommunity::class.java),
            listOf()
        )
    }

    private fun createDemoCommunity(): OverlayConfiguration<DemoCommunity> {
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            Overlay.Factory(DemoCommunity::class.java),
            listOf(randomWalk)
        )
    }

    private fun createMarketCommunity(): OverlayConfiguration<MarketCommunity> {
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            Overlay.Factory(MarketCommunity::class.java),
            listOf(randomWalk)
        )
    }

    private fun createCoinCommunity(): OverlayConfiguration<CoinCommunity> {
        val randomWalk = RandomWalk.Factory()
        val nsd = NetworkServiceDiscovery.Factory(getSystemService()!!)

        return OverlayConfiguration(
            Overlay.Factory(CoinCommunity::class.java),
            listOf(randomWalk, nsd)
        )
    }

    private fun createVotingCommunity(): OverlayConfiguration<VotingCommunity> {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "voting.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            VotingCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    private fun createMusicCommunity(): OverlayConfiguration<MusicCommunity> {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "music.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            MusicCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    private fun createRecommenderCommunity(): OverlayConfiguration<RecommenderCommunity> {
        val settings = TrustChainSettings()
        val musicDriver = AndroidSqliteDriver(Database.Schema, this, "music.db")
        val musicStore = TrustChainSQLiteStore(Database(musicDriver))
        val driver = AndroidSqliteDriver(Database.Schema, this, "recommend.db")
        val recommendStore = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            RecommenderCommunity.Factory(settings, recommendStore, musicStore),
            listOf(randomWalk)
        )
    }

    private fun getPrivateKey(): PrivateKey {
        // Load a key from the shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val privateKey = prefs.getString(PREF_PRIVATE_KEY, null)
        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = AndroidCryptoProvider.generateKey()
            prefs.edit()
                .putString(PREF_PRIVATE_KEY, newKey.keyToBin().toHex())
                .apply()
            newKey
        } else {
            AndroidCryptoProvider.keyFromPrivateBin(privateKey.hexToBytes())
        }
    }

    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val BLOCK_TYPE = "demo_block"
    }
}
