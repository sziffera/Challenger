package com.sziffer.challenger.model.challenge

data class PublicRouteItem(
    override val latLng: MyLatLng = MyLatLng(),
    override val time: Long = 0,
    override val distance: Float = 0f
) : RouteItemBase()