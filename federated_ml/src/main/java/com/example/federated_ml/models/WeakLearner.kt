package com.example.federated_ml.models
import com.example.federated_ml.models.feature_based.Adaline
import com.example.federated_ml.models.feature_based.Pegasos
import kotlin.random.Random

class WeakLearner(id: Int, songsHistory: Array<Int>, shouldHaveLocalModel: Boolean) {
    // total amount of known songs
    private val amountSongs = 10

    private val AMOUNT_MODELS = 5
    private val leanerId = id
    private var ensemmbleModel: OnlineModel? = null
    private var modelCache = mutableListOf<OnlineModel>()

    private var features: Array<Array<Double>> = Array(amountSongs) { _ -> Array<Double>(amountSongs) { Random.nextDouble(0.0, 5.0) } }
    private var labels = IntArray(amountSongs) { Random.nextInt(0, 2) }

    init {
        println("Init weak learner $leanerId")

        if (shouldHaveLocalModel) {
            initEnsembleModel()
        }

        initFeatures(songsHistory)
    }

    fun initFeatures(songsHistory: Array<Int>) {
        // map identical songs
        val featureMatrix = Array(amountSongs) { row ->
            Array(amountSongs) { col ->
                if (songsHistory.contains(row) and songsHistory.contains(col)) 1.0 else 0.0
            }
        }
        features = featureMatrix
        labels = Array(amountSongs) { song -> if (songsHistory.contains(song)) 1 else 0 }.toIntArray()
    }

    fun initEnsembleModel() {
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
        if (modelCache.size > AMOUNT_MODELS) {
            modelCache.removeAt(0)
        }

        return if (this.ensemmbleModel != null) {
            createModelUM(incomingModel)
        } else {
            createModelRW(incomingModel)
        }
    }

    fun makePrediction(testFeatures: Array<Array<Double>>) {
        val prediction: DoubleArray = if (this.ensemmbleModel != null) {
            this.ensemmbleModel!!.predict(testFeatures)
        } else {
            this.modelCache.last().predict(testFeatures)
        }
        println("Prediction for sample of learner $leanerId : ")
        for (i in testFeatures[0]) {
            print(i)
            print(", ")
        }
        println()
        print("is a label (from model 1): ")
        for (i in prediction) {
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

    fun mergeFeatureModel(incomingModel: OnlineModel): OnlineModel {
        val newModel = ensemmbleModel!!.merge(incomingModel)
        newModel.update(features, labels)
        return incomingModel
    }
}
