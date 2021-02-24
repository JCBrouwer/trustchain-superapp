package com.example.federated_ml
import com.example.federated_ml.models.Adaline
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import nl.tudelft.ipv8.attestation.trustchain.ANY_COUNTERPARTY_PK
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_SIG
import nl.tudelft.ipv8.attestation.trustchain.GENESIS_HASH
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.sqldelight.Database
import java.util.*
import kotlin.random.Random


class WeakLearner(id: Int, songsHistory: Array<Int>, shouldHaveLocalModel: Boolean) {
    // total amount of known songs
    private val amountSongs = 10

    private val AMOUNT_MODELS = 5
    private val leanerId = id
    private var ensemmbleModel: OnlineModel? = null
    private var modelCache = mutableListOf<OnlineModel>()

    private var features: Array<Array<Double>> = Array(amountSongs) { _ -> Array<Double>(amountSongs){ Random.nextDouble(0.0, 5.0) }}
    private var labels = IntArray(amountSongs) { Random.nextInt(0, 2) }

    init {
        println("Init weak learner $leanerId")

        if (shouldHaveLocalModel){
            initEnsembleModel()
        }

        initFeatures(songsHistory)
    }

    fun initFeatures(songsHistory: Array<Int>){
        // map identical songs
        val feature_matrix = Array(amountSongs) { row -> Array(amountSongs) { col ->
            if (songsHistory.contains(row) and songsHistory.contains(col)) 1.0 else 0.0 }}
        features = feature_matrix
        labels = Array(amountSongs) { song -> if (songsHistory.contains(song)) 1 else 0 }.toIntArray()
    }

    fun initEnsembleModel(){
        val model1 = Pegasos(0.1, features.size, 5)
        model1.update(features, labels)
        val model2 = Adaline(0.1, features.size)
        model2.update(features, labels)

        modelCache.add(model1)
        modelCache.add(model2)

        ensemmbleModel = model1.merge(model2)
    }

    fun updateWithNewModel(incomingModel: OnlineModel): OnlineModel {
        modelCache.add(incomingModel)
        if (modelCache.size > AMOUNT_MODELS){
            modelCache.removeAt(0)
        }

        return if (this.ensemmbleModel != null){
            createModelUM(incomingModel)
        } else {
            createModelRW(incomingModel)
        }
    }

    fun makePrediction(testFeatures: Array<Array<Double>>){
        var prediction: IntArray

        if (this.ensemmbleModel != null){
            prediction = this.ensemmbleModel!!.predict(testFeatures)
        } else {
            prediction = this.modelCache.last().predict(testFeatures)
        }
        println("Prediction for sample of learner $leanerId : ")
        for(i in testFeatures[0]){
            print(i)
            print(", ")
        }
        println()
        print("is a label (from model 1): ")
        for(i in prediction){
            print(i)
        }
        println()
    }

    fun createModelRW(incomingModel: OnlineModel): OnlineModel {
        incomingModel.update(features, labels)
        return incomingModel
    }

    fun createModelUM(incomingModel: OnlineModel): OnlineModel {
        incomingModel.update(features, labels)
        return ensemmbleModel!!.merge(incomingModel)
    }

    fun createModelMU(incomingModel: OnlineModel): OnlineModel {
        val newModel = ensemmbleModel!!.merge(incomingModel)
        newModel.update(features, labels)
        return incomingModel
    }
}
