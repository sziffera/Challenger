package com.sziffer.challenger.model.challenge

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class MyLocation(
    val distance: Float,
    val hr: Int = -1,
    val time: Long,
    val speed: Float,
    val altitude: Double,
    val latLng: LatLng
) : Parcelable