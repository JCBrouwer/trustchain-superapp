package com.example.federated_ml
import smile.classification.DiscreteNaiveBayes
import smile.data.formula.Feature
import smile.math.kernel.MercerKernel


class WeakLearner(id: Int, kernel: MercerKernel<Any>?, classifierType: DiscreteNaiveBayes.Model,
                  amountFeatures: Int) {
    protected val leaner_id = id
    protected var kernel: Any? = null
    protected lateinit var model: DiscreteNaiveBayes

    init {
        println("Init weak learner")
        initModel(kernel, classifierType, amountFeatures)
    }

    fun initModel(kernel: MercerKernel<Any>?, classifierType: DiscreteNaiveBayes.Model, amountFeatures: Int){
        this.kernel = kernel
        this.model = DiscreteNaiveBayes(classifierType, 2, amountFeatures)
    }


    fun retrainWithNewData(features: Array<IntArray>, labels: IntArray){
        if (this::model.isInitialized){
            model.update(features, labels)
        } else {
            println("Something went wrong with model initialization...")
        }
    }

    fun makePrediction(features: Array<IntArray>){
        var prediction = this.model.predict(features)
        println("Prediction for sample: ")
        for(i in features[0]){
            print(i)
            print(", ")
        }
        println()
        print("is a label: ")
        for(i in prediction){
            print(i)
        }
    }
}
