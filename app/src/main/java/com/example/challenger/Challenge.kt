package com.example.challenger

import android.location.Location

data class Challenge (
    //name
    val n: String = "",
    val type: String = "",
    val dst: Double = 0.0,
    //max speed
    val mS : Double = 0.0,
    val avg: Double = 0.0,
    val dur: Double = 0.0,
    //route
    val r: ArrayList<Location>? = null
)