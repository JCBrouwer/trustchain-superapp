package com.example.federated_ml.db

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.federated_ml.ipv8.SerializableSparseArray
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.parse
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File
import nl.tudelft.federated_ml.sqldelight.Database
import org.json.JSONObject

open class RecommenderStore(
    private val musicStore: TrustChainSQLiteStore,
    private val database: Database
) {
    lateinit var key: ByteArray
    private val musicDir = File("")
    private val totalAmountFeatures = 6
    private var artistsMap: HashMap<String, Double> = hashMapOf()

    fun storeModelLocally(model: OnlineModel) {
        database.dbModelQueries.addModel(
            name = model.name,
            type = model.name,
            parameters = model.serialize())
    }

    fun getLocalModel(): OnlineModel {
        val dbModel = database.dbModelQueries.getModel(name = "Pegasos").executeAsOneOrNull()
        return if (dbModel != null) {
            Log.i("Recommend", "Load existing local model")
            val model = Json.decodeFromString(dbModel.parameters) as Pegasos
            model
        } else {
            val model = Pegasos(0.01, totalAmountFeatures, 10)
            val trainingData = getLocalSongData()
            if (trainingData.first.isNotEmpty()){
                model.update(trainingData.first, trainingData.second)
            }
            storeModelLocally(model)
            Log.i("Recommend", "Initialized local model")
            Log.w("Model type", model.name)
            model
        }
    }

    private fun processSongs(limit: Int = 50): Pair<Array<Array<Double>>, IntArray> {
        val processedPair = this.getPlayCounts(limit)
        val localSongs = processedPair.first
        val features = Array(localSongs.size) { _ -> Array(totalAmountFeatures) { _ -> 0.0 } }
        for (i in processedPair.second.indices) {
            features[i] = extractMP3Features(processedPair.second[i])
        }
        return Pair(features, localSongs)
    }

    private fun extractBlockFeatures(block: TrustChainBlock): Array<Double> {
        var dataLen = -1.0
        var bpm = -1.0
        var year = -1.0
        var wmp = -1.0
        var artist = -1.0
        var genre = -1.0

        if (block.transaction["artist"] != null) {
            artist = artistsMap[block.transaction["artist"].toString()]!!
        }

        if (block.transaction["year"] != null) {
            year = block.transaction["year"] as Double
        }

        // TODO: fix this, below features are not
        //  present in transaction but rather in mp3fil
        if (block.transaction["genre"] != null) {
            genre = block.transaction["genre"] as Double
        }

        if (block.transaction["wmp"] != null) {
            wmp = block.transaction["wmp"] as Double
        }

        if (block.transaction["bpm"] != null) {
            bpm = block.transaction["bmp"] as Double
        }

        if (block.transaction["dataLen"] != null) {
            dataLen = block.transaction["dataLen"] as Double
        }

        return arrayOf(artist, year, wmp, bpm, dataLen, genre)
    }

    private fun processGlobalSongs(songsHistory: List<TrustChainBlock>): Array<Array<Double>> {
        val features = Array(songsHistory.size) { _ -> Array(totalAmountFeatures) { _ -> 0.0 } }
        for (i in songsHistory.indices) {
            features[i] = extractBlockFeatures(songsHistory[i])
        }
        return features
    }

    fun getLocalSongData(limit: Int = 50): Pair<Array<Array<Double>>, IntArray> {
        val processed = processSongs(limit)
        val features = processed.first
        val playcounts = processed.second
        return Pair(features, playcounts)
    }

    fun getNewSongs(limit: Int): Pair<Array<Array<Double>>, List<TrustChainBlock>> {
        var songsHistory = musicStore.getBlocksWithType("publish_release")
        try {
            songsHistory = songsHistory.subList(0, limit)
        } catch (e: java.lang.Exception){
            Log.w("Exception getNewSongs", e.toString())
        }
        val data = processGlobalSongs(songsHistory)
        return Pair(data, songsHistory)
    }


    private fun extractMP3Features(mp3File: Mp3File): Array<Double> {
        var dataLen = -1.0
        var bpm = -1.0
        var year = -1.0
        var wmp = -1.0
        var artist = -1.0
        var genre = -1.0

        if (mp3File.hasId3v2Tag()) {
            bpm = mp3File.id3v2Tag.bpm.toDouble()
            dataLen = mp3File.id3v2Tag.dataLength.toDouble()
            if (mp3File.id3v2Tag.year != null) {
                year = mp3File.id3v2Tag.year.toDouble()
            }
            wmp = mp3File.id3v2Tag.wmpRating.toDouble()
            if (mp3File.id3v2Tag.artist != null) {
                artist = this.artistsMap[mp3File.id3v2Tag.artist as String]!!
            }
            genre = mp3File.id3v2Tag.genre.toDouble()
        }
        if (mp3File.hasId3v1Tag()) {
            if (year == -1.0 && mp3File.id3v1Tag.year != null) {
                year = mp3File.id3v1Tag.year.toDouble()
            }
            if (artist == -1.0 && mp3File.id3v1Tag.artist != null) {
                artist = this.artistsMap[mp3File.id3v1Tag.artist as String]!!
            }
            if (genre == -1.0){
                genre = mp3File.id3v1Tag.genre.toDouble()
            }
        }

        return arrayOf(artist, year, wmp, bpm, dataLen, genre)
    }

    fun getPlayCounts(limit: Int = 50): Pair<IntArray, Array<Mp3File>> {
        if (!musicDir.isDirectory) return Pair(IntArray(0), arrayOf())

        val allFiles = musicDir.listFiles() ?: return Pair(IntArray(0), arrayOf())

        var labels = intArrayOf()
        var allMP3: Array<Mp3File> = arrayOf()
        var idx = 0
        for (albumFile in allFiles) {
            if (albumFile.isDirectory) {
                val audioFiles = albumFile.listFiles(AudioFileFilter()) ?: continue
                for (f in audioFiles){
                    try {
                        val mp3File = Mp3File(audioFiles[0])

                        // TODO: either get proper playcounts or
                        //  distinguish based on full/partial download...
                        labels += 1
                        allMP3 += mp3File
                    } catch (e: Exception) {
                    }
                    idx += 1

                    if (idx == limit){
                        return Pair(labels, allMP3)
                    }
                }
            }
        }
        return Pair(labels, allMP3)
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
