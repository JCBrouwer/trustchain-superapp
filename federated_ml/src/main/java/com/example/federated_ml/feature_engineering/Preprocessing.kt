package com.example.federated_ml.feature_engineering
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt


class Preprocessing{
    // Just create dummy training data for now
    companion object {
        @JvmStatic
        fun preprocessFeatures(features: Array<IntArray>, modelType: String): Array<IntArray> {
            if (modelType == "DiscreteNaiveBayes"){
                val processedFeatures = features
                return processedFeatures
            } else {
                return features
            }
        }

        @JvmStatic
        fun preprocessLabels(labels: IntArray): IntArray {
            var processedLabels = labels
            return processedLabels
        }
    }

}
