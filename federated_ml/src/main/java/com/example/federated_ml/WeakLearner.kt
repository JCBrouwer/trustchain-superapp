package com.example.federated_ml
import com.example.federated_ml.models.Adaline
import com.example.federated_ml.models.Pegasos


class WeakLearner(id: Int, amountFeatures: Int) {
    protected val leanerId = id
    protected val model1 = Pegasos(0.1, amountFeatures, 5)
    protected val model2 = Adaline(0.1, amountFeatures)

    init {
        println("Init weak learner")
    }

    fun retrainWithNewData(features: Array<Array<Double>>, labels: IntArray){
        model1.update(features, labels)
        model2.update(features, labels)
    }

    fun makePrediction(features: Array<Array<Double>>){
        var prediction1 = this.model1.predict(features)
        var prediction2 = this.model1.predict(features)
        println("Prediction for sample: ")
        for(i in features[0]){
            print(i)
            print(", ")
        }
        println()
        print("is a label (from model 1): ")
        for(i in prediction1){
            print(i)
        }

        println()
        print("is a label (from model 2): ")
        for(i in prediction2){
            print(i)
        }
    }
}
