package com.example.federated_ml.models
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.ANY_COUNTERPARTY_PK
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_SIG
import nl.tudelft.ipv8.attestation.trustchain.GENESIS_HASH
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.sqldelight.Database

import smile.classification.OnlineClassifier
import java.util.*

@Serializable
open class OnlineModel : OnlineClassifier<Array<Double>> {
    internal var weights: Array<Double>
    constructor(amountFeatures: Int) {
        weights = Array(amountFeatures) { _ -> Random().nextDouble() * 3 }
    }

    fun merge(otherOnlineModel: OnlineModel): OnlineModel {
        for (idx in weights.indices) {
            weights[idx] = (weights[idx] + otherOnlineModel.weights[idx]) / 2
        }
        return this
    }

    override fun predict(x: Array<Array<Double>>): IntArray {
        val result = IntArray(x.size)
        for ((idx, item) in x.withIndex()) {
            result[idx] = predict(item)
        }

        return result
    }

    fun score(x: Array<Array<Double>>, y: IntArray): Double {
        var correct = 0.0
        for (i in x.indices) {
            val output = predict(x[i])
            if (output == y[i]) {
                correct ++
            }
        }

        return (correct / x.size)
    }

    override fun update(x: Array<Array<Double>>, y: IntArray) {}

    override fun predict(x: Array<Double>): Int {
        return 1
    }

    override fun update(x: Array<Double>, y: Int) {}

    @ImplicitReflectionSerializer
    @OptIn(UnstableDefault::class)
    open fun serialize(): String {
        return Json.toJson(this).toString()
    }
}
