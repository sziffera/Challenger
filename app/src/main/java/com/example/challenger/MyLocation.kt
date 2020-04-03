package com.example.challenger

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MyLocation(
    val distance: Float,
    val time: Long,
    val speed: Float,
    val latLng: LatLng
) : Parcelable