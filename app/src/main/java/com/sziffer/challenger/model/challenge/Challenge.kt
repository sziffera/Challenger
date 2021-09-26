package com.sziffer.challenger.model.challenge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
    var routeAsString: String = "",
    var elevGain: Int = 0,
    var elevLoss: Int = 0
) : Parcelable
