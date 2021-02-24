package com.example.federated_ml.models
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.ANY_COUNTERPARTY_PK
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_SIG
import nl.tudelft.ipv8.attestation.trustchain.GENESIS_HASH
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.sqldelight.Database
import java.util.*


class Adaline(learningRate: Double, amountFeatures: Int):
    OnlineModel(amountFeatures) {
    private val learningRate = learningRate
    private var bias = Random().nextDouble()

    override fun update(x: Array<Double>, y: Int){
        var error = y - activation(forward(x))
        this.bias += this.learningRate * error
        for((idx, item) in x.withIndex()){
            weights[idx] += learningRate * error * item
        }
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {
        require(x.size == y.size) {
            String.format("Input vector x of size %d not equal to length %d of y", x.size, y.size)
        }
        for (i in x.indices) {
            update(x[i], y[i])
        }
    }

    override fun predict(x: Array<Double>): Int {
        if (activation(forward(x)) >= 0.0) {
            return 1
        } else {
            return 0
        }
    }

    // Linear activation function for now
    private fun activation(x: Double): Double {
        return x
    }

    private fun forward(x: Array<Double>): Double {
        var weightedSum = this.bias
        for(pair in this.weights.zip(x)){
            weightedSum += pair.first * pair.second
        }

        return weightedSum
    }

}

