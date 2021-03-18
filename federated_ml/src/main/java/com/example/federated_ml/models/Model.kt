package com.example.federated_ml.models

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
open class Model(open val name: String) {
    open fun serialize(): String {
        return Json.encodeToString(this)
    }

    open fun update() {}
}
