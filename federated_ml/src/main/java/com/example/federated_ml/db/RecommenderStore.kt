package com.example.federated_ml.db

import android.annotation.SuppressLint
import android.util.Log
import com.example.federated_ml.Essentia
import com.example.federated_ml.models.*
import com.example.federated_ml.models.collaborative_filtering.MatrixFactorization
import com.example.federated_ml.models.feature_based.Adaline
import com.example.federated_ml.models.feature_based.Pegasos
import com.example.musicdao_datafeeder.AudioFileFilter
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nl.tudelft.federated_ml.sqldelight.Database
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.log10

open class RecommenderStore(
    private val musicStore: TrustChainSQLiteStore,
    private val database: Database
) {
    lateinit var key: ByteArray
    // TODO: fix this to proper path
    @SuppressLint("SdCardPath")
    private val musicDir = File("/data/user/0/nl.tudelft.trustchain/cache/")
    val totalAmountFeatures = 5

    fun storeModelLocally(model: Model) {
        if (model.name == "MatrixFactorization") {
            database.dbModelQueries.addModel(
                name = model.name,
                type = model.name,
                parameters = (model as MatrixFactorization).serialize(private = true)
            )
        } else if (model.name == "Pegasos") {
            database.dbModelQueries.addModel(
                name = model.name,
                type = model.name,
                parameters = (model as Pegasos).serialize()
            )
        } else {
            database.dbModelQueries.addModel(
                name = model.name,
                type = model.name,
                parameters = (model as Adaline).serialize()
            )
        }
    }

    fun getLocalModel(name: String): Model {
        Log.w("Recommend", "Loading $name")
        val dbModel = database.dbModelQueries.getModel(name).executeAsOneOrNull()
        val model: Model
        if (name == "Adaline") {
            if (dbModel != null) {
                Log.i("Recommend", "Load existing local model")
                model = Json.decodeFromString<Adaline>(dbModel.parameters)
            } else {
                model = Adaline(0.1, totalAmountFeatures)
                Log.i("Recommend", "Initialized local model")
                Log.w("Model type", model.name)
            }
        } else if (name == "Pegasos") {
            if (dbModel != null) {
                Log.i("Recommend", "Load existing local model")
                model = Json.decodeFromString<Pegasos>(dbModel.parameters)
            } else {
                model = Pegasos(0.01, totalAmountFeatures, 10)
                Log.i("Recommend", "Initialized local model")
                Log.w("Model type", model.name)
            }
        } else {
            if (dbModel != null) {
                Log.i("Recommend", "Load existing local model")
                model = Json.decodeFromString<MatrixFactorization>(dbModel.parameters)
            } else {
                model = MatrixFactorization(numSongs = 0,
                    songNames = HashSet<String>(0),
                    ratings = Array<Double>(0) { _ -> 0.0 })
                Log.i("Recommend", "Initialized local model")
                Log.w("Model type", model.name)
            }
        }
        val trainingData = getLocalSongData()
        if (trainingData.first.isNotEmpty() && (name == "Pegasos" || name == "Adaline")) {
            (model as OnlineModel).update(trainingData.first, trainingData.second)
        }
        storeModelLocally(model)
        Log.i("Recommend", "Model completely loaded")
        return model
    }

    // TODO do getSongIds & getPlaycounts return the same size and order array in all cases?
    fun getPlaycounts(): Array<Double> {
        val songBlocks = musicStore.getBlocksWithType("publish_release")
        val playcounts = Array(globalSongCount()) { _ -> 0.0 }
        for ((i, block) in songBlocks.withIndex()) {
            if (block.transaction["title"] != null && block.transaction["artist"] != null) {
                playcounts[i] = database.dbFeaturesQueries.getFeature("local-${block.transaction["title"]}-${block.transaction["artist"]}").executeAsOneOrNull()?.count?.toDouble() ?: 0.0
            }
        }
        return playcounts
    }

    fun getSongIds(): Set<String> {
//        return database.dbFeaturesQueries.getSongIds().executeAsList().toSet()
        val songBlocks = musicStore.getBlocksWithType("publish_release")
        val songIds = HashSet<String>()
        for (block in songBlocks) {
            if (block.transaction["title"] != null && block.transaction["artist"] != null) {
                songIds.add("${block.transaction["title"]}-${block.transaction["artist"]}")
            }
        }
        return songIds
    }

    fun updateLocalFeatures(file: File) {
        val mp3File = Mp3File(file)
        val k = if (mp3File.id3v2Tag != null)
            "local-${mp3File.id3v2Tag.title}-${mp3File.id3v2Tag.artist}"
        else if (mp3File.id3v1Tag != null)
            "local-${mp3File.id3v1Tag.title}-${mp3File.id3v1Tag.artist}"
        else
            return
        val existingFeature = database.dbFeaturesQueries.getFeature(key = k).executeAsOneOrNull()
        var count = 1
        if (existingFeature != null) {
            count = existingFeature.count.toInt() + 1
            Log.i("Recommend", "Song exists! Increment counter")
        }
        val mp3Features = extractMP3Features(mp3File)
        database.dbFeaturesQueries.addFeature(
            key = k,
            song_year = mp3Features[0],
            wmp = mp3Features[1], bpm = mp3Features[2],
            dataLen = mp3Features[3], genre = mp3Features[4], count = count.toLong()
        )
    }

    private fun blockMetadata(block: TrustChainBlock): Array<Double> {
        var dataLen = -1.0
        var bpm = -1.0
        var year = -1.0
        var wmp = -1.0
        var genre = -1.0

        if (block.transaction["date"] != null) {
            try {
                year = Integer.parseInt(block.transaction["date"] as String).toDouble()
            } catch (e: Exception) {
                System.out.println(block.transaction["date"])
            }

            try {
                // 01/02/2020 case
                year = Integer.parseInt(
                    (block.transaction["date"] as
                        String).split("/").toTypedArray()[-1]
                ).toDouble()
            } catch (e: Exception) {
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

    fun globalSongCount(): Int {
        val s = getSongBlockMetadata(musicStore.getBlocksWithType("publish_release")).size
        return s
    }

    private fun getSongBlockMetadata(songsHistory: List<TrustChainBlock>): Array<Array<Double>> {
        val features = Array(songsHistory.size) { _ -> Array(totalAmountFeatures) { _ -> 0.0 } }
        for (i in songsHistory.indices) {
            features[i] = blockMetadata(songsHistory[i])
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
                            database.dbFeaturesQueries.addFeature(
                                key = k,
                                song_year = mp3Features[0],
                                wmp = mp3Features[1],
                                bpm = mp3Features[2],
                                dataLen = mp3Features[3],
                                genre = mp3Features[4],
                                count = count.toLong()
                            )
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
        val batch = database.dbFeaturesQueries.getAllFeatures().executeAsList()
        if (batch.isEmpty()) {
            Log.w(
                "Recommend",
                "No features in database, feature extraction still running in background?"
            )
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

    fun getNewSongs(): Pair<Array<Array<Double>>, List<TrustChainBlock>> {
        val songsHistory = musicStore.getBlocksWithType("publish_release")
        val data = getSongBlockMetadata(songsHistory)
        return Pair(data, songsHistory)
    }

    private fun extractMP3Features(mp3File: Mp3File): Array<Double> {
        var features = Array<Double>(225) {-1.0}
        val year = (mp3File.id3v2Tag ?: mp3File.id3v1Tag)?.year?.toDouble() ?: -1.0
        val genre = (mp3File.id3v2Tag ?: mp3File.id3v1Tag)?.genre?.toDouble() ?: -1.0
        try {
            val filename = mp3File.filename;
            val jsonfile = filename.replace(".mp3", ".json")
            if (!File(jsonfile).exists()) {
                if (Essentia.extractData(filename, jsonfile) == 1) {
                    Log.e("Feature extraction", "Error extracting data with Essentia")
                } else {
                    Log.i("Feature extraction","Got essentia features for $filename")
                }
            }
            val essentiaFeatures = JSONObject(File(jsonfile).bufferedReader().use { it.readText() })

            val lowlevel = essentiaFeatures.getJSONObject("lowlevel")
            val tonal = essentiaFeatures.getJSONObject("tonal")
            val rhythm = essentiaFeatures.getJSONObject("rhythm")
            val metadata = essentiaFeatures.getJSONObject("metadata")

            val length = metadata.getJSONObject("audio_properties").getDouble("length")
            val replay_gain = metadata.getJSONObject("audio_properties").getDouble("replay_gain")

            val dynamic_complexity = lowlevel.getDouble("dynamic_complexity")
            val average_loudness = lowlevel.getDouble("average_loudness")
            val integrated_loudness = lowlevel.getJSONObject("loudness_ebu128").getDouble("integrated")
            val loudness_range = lowlevel.getJSONObject("loudness_ebu128").getDouble("loudness_range")
            val momentary =  stats(lowlevel.getJSONObject("loudness_ebu128").getJSONObject("momentary"))
            val short_term = stats(lowlevel.getJSONObject("loudness_ebu128").getJSONObject("short_term"))
            val keys = arrayOf(
                "barkbands_crest", "barkbands_flatness_db", "barkbands_kurtosis", "barkbands_skewness", "barkbands_spread",
                "dissonance", "erbbands_crest", "erbbands_flatness_db", "erbbands_kurtosis", "erbbands_skewness",
                "erbbands_spread", "hfc", "melbands_crest", "melbands_flatness_db", "melbands_kurtosis", "melbands_skewness",
                "melbands_spread", "pitch_salience", "spectral_centroid", "spectral_complexity", "spectral_decrease",
                "spectral_energy", "spectral_energyband_high", "spectral_energyband_low", "spectral_energyband_middle_high",
                "spectral_energyband_middle_low", "spectral_entropy", "spectral_flux", "spectral_kurtosis", "spectral_rms",
                "spectral_rolloff", "spectral_skewness", "spectral_spread", "spectral_strongpeak", "zerocrossingrate")
            val lowlevelStats = Array(keys.size) {Array(5) {-1.0} }
            for ((i,key) in keys.withIndex()) {
                lowlevelStats[i] = stats(lowlevel.getJSONObject(key))
            }

            val key = scale2label(tonal.getString("chords_key"), tonal.getString("chords_scale"))
            val key_edma = scale2label(
                tonal.getJSONObject("key_edma").getString("key"),
                tonal.getJSONObject("key_edma").getString("scale")
            )
            val key_krumhansl = scale2label(
                tonal.getJSONObject("key_krumhansl").getString("key"),
                tonal.getJSONObject("key_krumhansl").getString("scale")
            )
            val key_temperley = scale2label(
                tonal.getJSONObject("key_temperley").getString("key"),
                tonal.getJSONObject("key_temperley").getString("scale")
            )
            val chords_strength = stats(tonal.getJSONObject("chords_strength"))
            val hpcp_crest = stats(tonal.getJSONObject("hpcp_crest"))
            val hpcp_entropy = stats(tonal.getJSONObject("hpcp_entropy"))
            val tuning_nontempered_energy_ratio = tonal.getDouble("tuning_nontempered_energy_ratio")
            val tuning_diatonic_strength = tonal.getDouble("tuning_diatonic_strength")

            val bpm = rhythm.getDouble("bpm")
            val danceability = rhythm.getDouble("danceability")
            val beats_loudness = stats(rhythm.getJSONObject("beats_loudness"))

            features = arrayOf(
                    arrayOf(
                        year, genre, length, replay_gain, dynamic_complexity, average_loudness,
                        integrated_loudness, loudness_range, bpm, danceability, tuning_nontempered_energy_ratio,
                        tuning_diatonic_strength
                    ),
                    momentary, short_term, lowlevelStats.flatten().toTypedArray(), key, key_edma, key_krumhansl, key_temperley,
                    chords_strength, hpcp_crest, hpcp_entropy, beats_loudness
            ).flatten().toTypedArray()
        } catch (e: Exception) {
            Log.e("Feature extraction", "Essentia extraction failed:")
            Log.e("Feature extraction", e.stackTraceToString())
        }

        // log transform on features
        for ((i, feat) in features.withIndex()) {
            features[i] = if (feat < 0.0) -log10(-feat) else if (feat > 0.0) log10(feat) else 0.0
        }
        return features
    }

    // positions of scales on the circle of fifths
    private val majorKeys = mapOf(
        "C" to 0.0, "G" to 1.0, "D" to 2.0, "A" to 3.0, "E" to 4.0, "B" to 5.0, "Gb" to 6.0,
        "F#" to 6.0, "Db" to 7.0, "Ab" to 8.0, "Eb" to 9.0, "Bb" to 10.0, "F" to 11.0
    )
    private val minorKeys = mapOf(
        "A" to 0.0, "E" to 1.0, "B" to 2.0, "F#" to 3.0, "C#" to 4.0, "G#" to 5.0, "D#" to 6.0,
        "Eb" to 6.0, "Bb" to 7.0, "F" to 8.0, "C" to 9.0, "G" to 10.0, "D" to 11.0
    )
    private fun scale2label(key: String, mode: String): Array<Double> {
        val keyCode = (if (mode == "major") majorKeys[key] else minorKeys[key]) ?: -1.0
        val modeCode = if (mode == "major") 0.0 else 1.0
        return arrayOf(keyCode, modeCode)
    }
    private fun stats(obj: JSONObject): Array<Double> {
        return arrayOf(
            obj.getDouble("min"),
            obj.getDouble("median"),
            obj.getDouble("mean"),
            obj.getDouble("max"),
            obj.getDouble("var"),
        )
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
