package com.sziffer.challenger.model.challenge

abstract class RouteItemBase {
    abstract val latLng: MyLatLng
    abstract val time: Long
    abstract val distance: Float
}