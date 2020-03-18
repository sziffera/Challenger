package com.example.challenger



import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Challenge(
    val id: String = "",
    var n: String = "",
    var type: String = "",
    val dst: Double = 0.0,
    //max speed
    val mS : Double = 0.0,
    val avg: Double = 0.0,
    val dur: Double = 0.0,
    //route
    val stringRoute: String = ""
) : Parcelable
