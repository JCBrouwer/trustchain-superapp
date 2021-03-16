package com.example.federated_ml.db

import android.annotation.SuppressLint
import android.util.Log
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File
import nl.tudelft.federated_ml.sqldelight.Database
import nl.tudelft.federated_ml.sqldelight.Features
import kotlin.collections.HashMap
import kotlin.random.Random

open class RecommenderStore(
    private val musicStore: TrustChainSQLiteStore,
    private val database: Database
) {
    lateinit var key: ByteArray
    // TODO: fix this to proper path
    @SuppressLint("SdCardPath")
    private val musicDir = File("/data/user/0/nl.tudelft.trustchain/cache/")
    private val totalAmountFeatures = 5

    fun storeModelLocally(model: OnlineModel) {
        database.dbModelQueries.addModel(
            name = model.name,
            type = model.name,
            parameters = model.serialize())
    }

    fun getLocalModel(): OnlineModel {
        val dbModel = database.dbModelQueries.getModel(name = "Pegasos").executeAsOneOrNull()
        val model: OnlineModel
        if (dbModel != null) {
            Log.i("Recommend", "Load existing local model")
            model = Json.decodeFromString(dbModel.parameters) as Pegasos
        } else {
            model = Pegasos(0.01, totalAmountFeatures, 10)
            Log.i("Recommend", "Initialized local model")
            Log.w("Model type", model.name)
        }
        val trainingData = getLocalSongData()
        if (trainingData.first.isNotEmpty()) {
            model.update(trainingData.first, trainingData.second)
        }
        storeModelLocally(model)
        return model
    }

    fun updateLocalFeatures(file: File) {
        val mp3File = Mp3File(file)
        val k = "local-${mp3File.id3v2Tag.title}-${mp3File.id3v2Tag.artist}"
        val existingFeature = database.dbFeaturesQueries.getFeature(key = k).executeAsOneOrNull()
        var count = 1
        if (existingFeature != null) {
            count = existingFeature.count.toInt() + 1
            Log.i("Recommend", "Song exists! Increment counter")
        }
        val mp3Features = extractMP3Features(mp3File)
        database.dbFeaturesQueries.addFeature(key = k,
            song_year = mp3Features[0],
            wmp = mp3Features[1], bpm = mp3Features[2],
            dataLen = mp3Features[3], genre = mp3Features[4], count = count.toLong())
    }

    private fun extractBlockFeatures(block: TrustChainBlock): Array<Double> {
        var dataLen = -1.0
        var bpm = -1.0
        var year = -1.0
        var wmp = -1.0
        var genre = -1.0

        if (block.transaction["date"] != null) {
            try {
                year = Integer.parseInt(block.transaction["date"] as String).toDouble()
            } catch (e:Exception) {
                System.out.println(block.transaction["date"])
            }

            try {
                // 01/02/2020 case
                year = Integer.parseInt((block.transaction["date"] as
                    String).split("/").toTypedArray()[-1]).toDouble()
            } catch (e:Exception) {
                System.out.println(block.transaction["date"])
            }
        }

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

        return arrayOf(year, wmp, bpm, dataLen, genre)
    }

    private fun processGlobalSongs(songsHistory: List<TrustChainBlock>): Array<Array<Double>> {
        val features = Array(songsHistory.size) { _ -> Array(totalAmountFeatures) { _ -> 0.0 } }
        for (i in songsHistory.indices) {
            features[i] = extractBlockFeatures(songsHistory[i])
        }
        return features
    }

    fun addAllLocalFeatures() {
        Log.w("Recommender Store", "Getting playcounts...")
        if (!musicDir.isDirectory) return

        val allFiles = musicDir.listFiles() ?: return
        Log.w("Recommender Store", "Amount of files is ${allFiles.size}")

        var idx = 0
        for (albumFile in allFiles) {
            Log.w("Recommender Store", "Local album is ${albumFile.name}")
            if (albumFile.isDirectory) {
                val audioFiles = albumFile.listFiles(AudioFileFilter()) ?: continue
                Log.w("Recommender Store", "Local songs amount in alum: ${audioFiles.size}")
                for (f in audioFiles) {
                    if (Mp3File(f).id3v2Tag != null) {
                        val updatedFile = Mp3File(f)
                        try {
                            val mp3Features = extractMP3Features(Mp3File(f))
                            val count = 1
                            val k = "local-${updatedFile.id3v2Tag.title}-${updatedFile.id3v2Tag.artist}"
                            database.dbFeaturesQueries.addFeature(key = k,
                                song_year = mp3Features[0],
                                wmp = mp3Features[1], bpm = mp3Features[2],
                                dataLen = mp3Features[3], genre = mp3Features[4], count = count.toLong())
                        } catch (e: Exception) {
                            Log.w("Init local features", e)
                        }
                        idx += 1
                    }
                }
            }
        }
    }

    fun getLocalSongData(): Pair<Array<Array<Double>>, IntArray> {
        var batch = database.dbFeaturesQueries.getAllFeatures().executeAsList()
        if (batch.size == 0) {
            addAllLocalFeatures()
            batch = database.dbFeaturesQueries.getAllFeatures().executeAsList()
        }

        val features = Array(batch.size) { _ -> Array(totalAmountFeatures) { _ -> 0.0 } }
        val playcounts = Array(batch.size) { _ -> 0 }.toIntArray()
        for (i in (0 until batch.size)) {
            // artist, year, wmp, bpm, dataLen, genre
            features[i][0] = batch[i].song_year!!
            features[i][1] = batch[i].wmp!!
            features[i][2] = batch[i].bpm!!
            features[i][3] = batch[i].dataLen!!
            features[i][4] = batch[i].genre!!
            playcounts[i] = batch[i].count.toInt()
            Log.w("Recommender Store", "Playcount is ${playcounts[i]} for song ${batch[i]}")
        }
        return Pair(features, playcounts)
    }

    fun getNewSongs(limit: Int): Pair<Array<Array<Double>>, List<TrustChainBlock>> {
        var songsHistory = musicStore.getBlocksWithType("publish_release")
        try {
            songsHistory = songsHistory.subList(0, limit)
        } catch (e: java.lang.Exception) {
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
        var genre = -1.0

        if (mp3File.hasId3v2Tag()) {
            try {
                bpm = mp3File.id3v2Tag.bpm.toDouble()
            } catch (e: Exception){
                Log.w("Feature extraction", e.toString())
                Log.w("Feature extraction", e.toString())
            }

            try {
                dataLen = mp3File.id3v2Tag.dataLength.toDouble()
            } catch (e: Exception){
                Log.w("Feature extraction", dataLen.toString())
                Log.w("Feature extraction", e.toString())
            }

            try {
                year = mp3File.id3v2Tag.year.toDouble()
            } catch (e: Exception){
                Log.w("Feature extraction", year.toString())
                Log.w("Feature extraction", e.toString())
            }

            try {
                wmp = mp3File.id3v2Tag.wmpRating.toDouble()
            } catch (e: Exception){
                Log.w("Feature extraction", wmp.toString())
                Log.w("Feature extraction", e.toString())
            }

            try {
                genre = mp3File.id3v2Tag.genre.toDouble()
            } catch (e: Exception){
                Log.w("Feature extraction", genre.toString())
                Log.w("Feature extraction", e.toString())
            }
        }
        if (mp3File.hasId3v1Tag()) {
            if (year == -1.0 && mp3File.id3v1Tag.year != null) {
                try {
                    year = mp3File.id3v1Tag.year.toDouble()
                } catch (e: Exception){
                    Log.w("Feature extraction", e.toString())
                }
            }

            if (genre == -1.0) {
                try {
                    genre = mp3File.id3v1Tag.genre.toDouble()
                } catch (e: Exception){
                    Log.w("Feature extraction", e.toString())
                }
            }
        }

        Log.w("Feature extraction", "$year $wmp $bpm $dataLen $genre")

        return arrayOf(year, wmp, bpm, dataLen, genre)
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
