package com.sziffer.challenger.model.challenge

import com.google.android.gms.maps.model.LatLng

abstract class RouteItemBase {
    abstract val latLng: LatLng
    abstract val time: Long
    abstract val distance: Float
}