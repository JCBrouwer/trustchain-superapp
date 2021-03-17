package com.example.federated_ml.db

import android.annotation.SuppressLint
import android.util.Log
import com.example.federated_ml.models.*
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import java.io.File
import nl.tudelft.federated_ml.sqldelight.Database
import nl.tudelft.federated_ml.sqldelight.Models

open class RecommenderStore(
    private val musicStore: TrustChainSQLiteStore,
    private val database: Database
) {
    lateinit var key: ByteArray
    // TODO: fix this to proper path
    @SuppressLint("SdCardPath")
    private val musicDir = File("/data/user/0/nl.tudelft.trustchain/cache/")
    val totalAmountFeatures = 6
    private var artistsMap: HashMap<String, Double> = hashMapOf()

    fun storeModelLocally(model: Model) {
        if (model.name == "MatrixFactorization") {
            database.dbModelQueries.addModel(
                name = model.name,
                type = model.name,
                parameters = (model as MatrixFactorization).serialize(private=true)
            )
        } else {
            database.dbModelQueries.addModel(
                name = model.name,
                type = model.name,
                parameters = model.serialize()
            )
        }
    }

    fun getLocalModel(name: String): Model? {
        Log.w("Recommend", "Loading $name")
        val dbModel = database.dbModelQueries.getModel(name).executeAsOneOrNull() ?: return null
        return if (name == "Adaline")
            Json.decodeFromString<Adaline>(dbModel.parameters)
        else if (name == "Pegasos")
            Json.decodeFromString<Pegasos>(dbModel.parameters)
        else
            Json.decodeFromString<MatrixFactorization>(dbModel.parameters)
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

    fun globalSongCount(): Int {
        return processGlobalSongs(musicStore.getBlocksWithType("publish_release")).size
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
        var artist = -1.0
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

            if (mp3File.id3v2Tag.artist != null) {
                try {
                    artist = this.artistsMap[mp3File.id3v2Tag.artist as String]!!
                } catch (e: java.lang.Exception){
                    artist = this.artistsMap.size.toDouble()
                    artistsMap[mp3File.id3v2Tag.artist as String] = artist
                }

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
            if (artist == -1.0 && mp3File.id3v1Tag.artist != null) {
                try {
                    artist = this.artistsMap[mp3File.id3v1Tag.artist as String]!!
                } catch (e: java.lang.Exception){
                    artist = this.artistsMap.size.toDouble()
                    artistsMap[mp3File.id3v1Tag.artist as String] = artist
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

        Log.w("Feature extraction", "$artist $year $wmp $bpm $dataLen $genre")

        return arrayOf(artist, year, wmp, bpm, dataLen, genre)
    }

    fun getPlayCounts(limit: Int = 50): Pair<IntArray, Array<Mp3File>> {
        Log.w("Recommender Store", "Getting playcounts...")
        if (!musicDir.isDirectory) return Pair(IntArray(0), arrayOf())

        val allFiles = musicDir.listFiles() ?: return Pair(IntArray(0), arrayOf())
        Log.w("Recommender Store", "Amount of files is ${allFiles.size}")

        var labels = intArrayOf()
        var allMP3: Array<Mp3File> = arrayOf()
        var idx = 0
        for (albumFile in allFiles) {
            Log.w("Recommender Store", "Local album is ${albumFile.name}")
            if (albumFile.isDirectory) {
                val audioFiles = albumFile.listFiles(AudioFileFilter()) ?: continue
                Log.w("Recommender Store", "Local songs amount in alum: ${audioFiles.size}")
                for (f in audioFiles) {
                    try {
                        val mp3File = Mp3File(f)
                        Log.w("Recommender Store", "Add training song $mp3File")

                        // TODO: either get proper playcounts or
                        //  distinguish based on full/partial download...
                        labels += 1
                        allMP3 += mp3File
                    } catch (e: Exception) {
                    }
                    idx += 1

                    if (idx == limit) {
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
