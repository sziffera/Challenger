package com.sziffer.challenger.utils.extensions

import android.content.res.Resources
import android.util.Log
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

fun Number.toPace(): String {
    val minutes = this.toDouble() / 60 % 60
    val seconds = this.toDouble() % 60
    Log.d("PACE", "${minutes.toInt()}:${seconds.toInt()}")
    val secondsFormatted = if (seconds > 9)
        seconds.toInt().toString()
    else
        "0${seconds.toInt()}"
    return "${minutes.toInt()}:$secondsFormatted"
}

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()