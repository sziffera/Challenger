package com.sziffer.challenger.utils.extensions

import com.sziffer.challenger.utils.getStringFromNumber
import kotlin.math.round

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun Float.round(decimals: Int): Float {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return (round(this * multiplier) / multiplier).toFloat()
}

fun Double.popToPercent(): String {
    return "${getStringFromNumber(0, this.times(100))}%"
}