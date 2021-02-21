package com.example.federated_ml
import com.example.federated_ml.models.Adaline
import com.example.federated_ml.models.OnlineModel
import com.example.federated_ml.models.Pegasos
import kotlin.random.Random


class WeakLearner(id: Int) {
    private val amountFeatures = 10
    private val amountSongs = 10

    private val AMOUNT_MODELS = 5
    private val leanerId = id
    private lateinit var ensemmbleModel: OnlineModel
    private var modelCache = mutableListOf<OnlineModel>()

    // user data, explicitly setting to random vars for now
    // TODO: lateinit and extract from user db later on
    private var features: Array<Array<Double>> = Array(amountSongs) { _ -> Array<Double>(amountFeatures){ Random.nextDouble(0.0, 5.0) }}
    private var labels = IntArray(amountSongs) { Random.nextInt(0, 2) }

    init {
        println("Init weak learner $leanerId")
        initEnsembleModel()
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

    fun updateWithNewModel(incomingModel: OnlineModel) {
        modelCache.add(incomingModel)
        if (modelCache.size > AMOUNT_MODELS){
            modelCache.removeAt(0)
        }
    }

    fun makePrediction(testFeatures: Array<Array<Double>>){
        val prediction = this.ensemmbleModel.predict(testFeatures)
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
        return ensemmbleModel.merge(incomingModel)
    }
    fun createModelMU(incomingModel: OnlineModel): OnlineModel {
        val newModel = ensemmbleModel.merge(incomingModel)
        newModel.update(features, labels)
        return incomingModel
    }
}
