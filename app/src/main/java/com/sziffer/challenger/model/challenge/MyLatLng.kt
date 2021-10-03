package com.sziffer.challenger.model.challenge

import android.location.Location
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MyLatLng(val latitude: Double = 0.0, val longitude: Double = 0.0) : Parcelable {
    constructor() : this(0.0, 0.0)
    constructor(location: Location) : this(location.latitude, location.longitude)
}