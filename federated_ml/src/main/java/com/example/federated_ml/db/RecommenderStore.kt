package com.example.federated_ml.db

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File
import nl.tudelft.federated_ml.sqldelight.Database

open class RecommenderStore(
    private val musicStore: TrustChainSQLiteStore,
    private val database: Database
) {
    lateinit var key: ByteArray
    private val musicDir = File("")

    fun storeModelLocally(model: OnlineModel) {
        database.dbModelQueries.addModel(
            name= model::class::simpleName.toString(),
            type= model::class::simpleName.toString(),
            parameters= model.serialize())
    }

    fun getLocalModel(): OnlineModel {
        val dbModel = database.dbModelQueries.getModel(name="Pegasos").executeAsOneOrNull()
        return if (dbModel != null) {
            Json.decodeFromString(dbModel.parameters) as Pegasos
        } else {
            val model = Pegasos(0.01, 20, 10)
            storeModelLocally(model)
            Log.i("Recommend", "Initialized local model")
            model
        }
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

    fun getNewSongs(limit: Int): Pair<Array<Array<Double>>, List<TrustChainBlock>>? {
        val data = getSongData(2 * limit)
        val features = data.first
        val playcounts = data.second
        val blocks = data.third
        if (features.isNotEmpty()) {
            val newFeatures =
                Array<Array<Double>>(limit) { _ -> Array(features[0].size) { _ -> 0.0 } }
            var j = 0
            for ((i, feature) in features.withIndex()) {
                if (playcounts[i] == 0) {
                    newFeatures[j] = feature
                    j += 1
                }
                if (j == limit) break
            }
            return Pair(newFeatures, blocks)
        } else {
            return null
        }
    }

    fun getSongData(limit: Int = 200): Triple<Array<Array<Double>>, IntArray, List<TrustChainBlock>> {
        val songsHistory = musicStore.getBlocksWithType("publish_release")
        Log.w("Recommend", "Songs in music store: " + songsHistory.size.toString())
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
            if (data.size == limit ) break
        }
        val processed = processSongs(data)
        val features = processed.first
        val playcounts = processed.second
        return Triple(features, playcounts, songsHistory)
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

    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: RecommenderStore
        fun getInstance(musicStore: TrustChainSQLiteStore, database: Database): RecommenderStore {
            if (!::instance.isInitialized) {
                instance = RecommenderStore(musicStore, database)
            }
            return instance
        }
    }
}
