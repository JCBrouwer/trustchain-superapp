package com.example.federated_ml.models.collaborative_filtering

operator fun Array<Double>.plus(other: Double): Array<Double> {
    for (i in 0 until size) this[i] += other
    return this
}

operator fun Double.plus(doubles: Array<Double>): Array<Double> {
    return doubles + this
}

operator fun Array<Double>.times(other: Double): Array<Double> {
    for (i in 0 until size) this[i] *= other
    return this
}

operator fun Double.times(doubles: Array<Double>): Array<Double> {
    return doubles * this
}

operator fun Array<Double>.div(other: Double): Array<Double> {
    for (i in 0 until size) this[i] /= other
    return this
}

operator fun Double.div(doubles: Array<Double>): Array<Double> {
    return doubles / this
}

operator fun Array<Double>.minus(other: Double): Array<Double> {
    for (i in 0 until size) this[i] -= other
    return this
}

operator fun Double.minus(doubles: Array<Double>): Array<Double> {
    return doubles - this
}

operator fun Array<Double>.plus(other: Array<Double>): Array<Double> {
    for (i in 0 until size) this[i] += other[i]
    return this
}

operator fun Array<Double>.minus(other: Array<Double>): Array<Double> {
    for (i in 0 until size) this[i] -= other[i]
    return this
}

operator fun Array<Double>.times(other: Array<Double>): Double {
    var out = 0.0
    for (i in 0 until size) out += this[i] * other[i]
    return out
}
