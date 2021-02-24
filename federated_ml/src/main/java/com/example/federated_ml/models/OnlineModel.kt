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
import kotlin.js.*

import smile.classification.OnlineClassifier
import java.util.*
import kotlin.collections.HashMap

open class OnlineModel (amountFeatures: Int): OnlineClassifier<Array<Double>> {
    internal var weights: Array<Double> = Array(amountFeatures) { _ -> Random().nextDouble() * 3}

    fun merge(otherOnlineModel: OnlineModel): OnlineModel{
        for (idx in weights.indices){
            weights[idx] = (weights[idx] + otherOnlineModel.weights[idx]) / 2
        }
        return this
    }

    override fun predict(x: Array<Array<Double>>): IntArray {
        val result  = IntArray(x.size)
        for((idx, item) in x.withIndex()){
            result[idx] = predict(item)
        }

        return result
    }

    fun score(x: Array<Array<Double>>, y: IntArray): Double{
        var correct = 0.0
        for (i in x.indices) {
            var output = predict(x[i])
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

    fun serialize(): String {
        val modelString = Json.encodeToString(this)
        return modelString
    }

    fun store(privateKey: ByteArray) {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        val store = TrustChainSQLiteStore(database)
        val modelBlock = TrustChainBlock(
            this::class::simpleName.toString(),
            this.serialize().toByteArray(Charsets.US_ASCII),
            privateKey,
            1u,
            ANY_COUNTERPARTY_PK,
            0u,
            GENESIS_HASH,
            EMPTY_SIG,
            Date()
        )
        store.addBlock(modelBlock)
    }

}
