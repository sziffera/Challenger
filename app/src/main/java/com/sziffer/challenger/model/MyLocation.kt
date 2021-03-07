package com.sziffer.challenger.model

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class MyLocation(
    val distance: Float,
    val time: Long,
    val speed: Float,
    val altitude: Double,
    val latLng: LatLng
) : Parcelable