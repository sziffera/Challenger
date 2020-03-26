package com.example.challenger



import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Challenge(
    val id: String = "",
    var n: String = "",
    var type: String = "",
    var dst: Double = 0.0,
    //max speed
    var mS: Double = 0.0,
    var avg: Double = 0.0,
    var dur: Long = 0,
    //route
    var stringRoute: String = ""
) : Parcelable
