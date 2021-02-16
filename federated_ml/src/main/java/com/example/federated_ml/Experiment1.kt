package com.example.federated_ml
import java.io.File
import org.openmined.syft.demo.federated.domain.Batch

class Experiment1 {

    private var features = File("./data/features.csv")
    private var labels = File("./data/labels.csv")

    fun load_kotlin_sift(){
        print("I work!")

    }

//    fun loadDataBatch(batchSize: Int): Pair<Batch, Batch> {
//        val trainInput = arrayListOf<List<Float>>()
//        val labels = arrayListOf<List<Float>>()
//        for (idx in 0..batchSize)
//            readSample(trainInput, labels)
//
//        val trainingData = Batch(
//            trainInput.flatten().toFloatArray(),
//            longArrayOf(trainInput.size.toLong(), trainDim)
//        )
//        val trainingLabel = Batch(
//            labels.flatten().toFloatArray(),
//            longArrayOf(labels.size.toLong(), labelDim)
//        )
//        return Pair(trainingData, trainingLabel)
//    }
//
//    private fun readSample(
//        trainInput: ArrayList<List<Float>>,
//        labels: ArrayList<List<Float>>
//    ) {
//        val sample = readLine()
//
//        trainInput.add(
//            sample.first.map { it.trim().toFloat() }
//        )
//        labels.add(
//            sample.second.map { it.trim().toFloat() }
//        )
//    }
}
