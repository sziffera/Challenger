package com.sziffer.challenger

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Challenge(
    val id: String = "",
    var firebaseId: String = "",
    var date: String = "",
    var name: String = "",
    var type: String = "",
    var dst: Double = 0.0,
    var mS: Double = 0.0,
    var avg: Double = 0.0,
    var dur: Long = 0,
    var routeAsString: String = ""
) : Parcelable
