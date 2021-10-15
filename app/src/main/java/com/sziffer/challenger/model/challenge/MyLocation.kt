package com.sziffer.challenger.model.challenge

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class MyLocation(
    override val distance: Float,
    val hr: Int = -1,
    override val time: Long,
    val speed: Float,
    var altitude: Double,
    override val latLng: LatLng
) : Parcelable, RouteItemBase()