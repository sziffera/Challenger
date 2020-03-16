package com.example.challenger



import android.location.Location
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Challenge(
    var n: String = "",
    var type: String = "",
    val dst: Double = 0.0,
    //max speed
    val mS : Double = 0.0,
    val avg: Double = 0.0,
    val dur: Double = 0.0,
    //route
    val r: ArrayList<Location>? = null
) : Parcelable
