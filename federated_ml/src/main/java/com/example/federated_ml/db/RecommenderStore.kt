package com.example.federated_ml.db

import com.example.federated_ml.models.OnlineModel
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File
import java.util.*
import kotlinx.serialization.decodeFromString
import nl.tudelft.ipv8.attestation.trustchain.*

class RecommenderStore(
    private val recommendStore: TrustChainSQLiteStore,
    private val musicStore: TrustChainSQLiteStore,
    private val musicDir: File
) {
    lateinit var key: ByteArray

    @kotlin.ExperimentalUnsignedTypes
    fun storeModel(model: OnlineModel) {
        val modelBlock = TrustChainBlock(
            model::class::simpleName.toString(),
            model.serialize().toByteArray(Charsets.US_ASCII),
            key,
            1u,
            ANY_COUNTERPARTY_PK,
            0u,
            GENESIS_HASH,
            EMPTY_SIG,
            Date()
        )
        recommendStore.addBlock(modelBlock)
    }

    fun storeSong(song: Map<String, String>) {
        val songBlock = TrustChainBlock(
            "publish_release",
            TransactionEncoding.encode(song),
            key,
            1u,
            ANY_COUNTERPARTY_PK,
            0u,
            GENESIS_HASH,
            EMPTY_SIG,
            Date()
        )
        musicStore.addBlock(songBlock)
    }

    fun getLocalModel(modelType: String): OnlineModel {
        return Json.decodeFromString(recommendStore.getBlocksWithType(modelType)[0].toString())
    }

    private fun processSongs(data: Array<Triple<String?, String?, String?>>): Pair<Array<Array<Double>>, IntArray> {
        val features = Array(data.size) { _ -> Array(data.size) { _ -> 0.0 } }
        val localSongs = this.getPlayCounts(data)
        for (i in data.indices) {
            for (j in i until data.size) {
                if (localSongs[i] == 1 && localSongs[j] == 1) {
                    features[i][j] = 1.0
                    features[j][i] = 1.0
                }
            }
        }
        return Pair(features, localSongs)
    }

    private fun getSongData(limit: Int = 1000): Pair<Array<Array<Double>>, IntArray> {

        val songsHistory = musicStore.getLatestBlocks(key, limit)
        val data = Array<Triple<String?, String?, String?>>(songsHistory.size) { _ ->
            Triple(null, null, null) }

        for ((i, block) in songsHistory.withIndex()) {
            var artist: String? = null
            var title: String? = null
            var year: String? = null

            if (block.transaction["artist"] != null) {
                artist = block.transaction["artist"].toString()
            }

            if (block.transaction["title"] != null) {
                title = block.transaction["title"].toString()
            }

            if (block.transaction["year"] != null) {
                year = block.transaction["year"].toString()
            }

            data[i] = Triple(artist, title, year)
        }

        return processSongs(data)
    }

    fun getData(): Pair<Array<Array<Double>>, IntArray> {
        // TODO get proper local dir with stored files
        val data = getSongData()
        val features = data.first
        val labels = data.second
        return Pair(features, labels)
    }

    private fun extractMP3Data(mp3File: Mp3File): Triple<String?, String?, String?> {
        var artist: String? = null
        var title: String? = null
        var year: String? = null

        if (mp3File.hasId3v2Tag()) {
            if (mp3File.id3v2Tag.albumArtist != null) {
                artist = mp3File.id3v2Tag.albumArtist
            }
            if (mp3File.id3v2Tag.albumArtist != null) {
                title = mp3File.id3v2Tag.album
            }
            if (mp3File.id3v1Tag.year != null) {
                year = mp3File.id3v2Tag.year
            }
            if (artist == null && mp3File.id3v2Tag.artist != null) {
                artist = mp3File.id3v2Tag.artist
            }
        }
        if (mp3File.hasId3v1Tag()) {
            if (artist == null && mp3File.id3v1Tag.artist != null) {
                artist = mp3File.id3v1Tag.artist
            }
            if (title == null && mp3File.id3v1Tag.album != null) {
                title = mp3File.id3v1Tag.album
            }
            if (title == null && mp3File.id3v1Tag.title != null) {
                title = mp3File.id3v1Tag.title
            }
            if (year == null && mp3File.id3v1Tag.year != null) {
                year = mp3File.id3v1Tag.year
            }
        }

        return Triple(artist, title, year)
    }

    fun getPlayCounts(songsFromBlock: Array<Triple<String?, String?, String?>>): IntArray {
        val labels = IntArray(songsFromBlock.size) { _ -> 0 }
        if (!musicDir.isDirectory) return labels

        val allFiles = musicDir.listFiles() ?: return labels

        var idx = 0
        for (albumFile in allFiles) {
            if (albumFile.isDirectory) {
                val audioFiles = albumFile.listFiles(AudioFileFilter()) ?: continue
                try {
                    val mp3File = Mp3File(audioFiles[0])
                    val mp3Data = extractMP3Data(mp3File)

                    if (mp3Data in songsFromBlock) {
                        labels[idx] = 1
                    }
                } catch (e: Exception) {
                }
                idx += 1
            }
        }
        return labels
    }
}
