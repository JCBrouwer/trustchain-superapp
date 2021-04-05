package com.example.federated_ml.models

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * General model class to accommodate both feature-based and
 * collaborative filtering models
 *
 * @property name model name
 */
@Serializable
open class Model(open val name: String) {
    open fun serialize(): String {
        return Json.encodeToString(this)
    }

    open fun update() {}
}
