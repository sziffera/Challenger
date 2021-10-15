package com.sziffer.challenger.model.challenge

import com.google.android.gms.maps.model.LatLng


data class PublicRouteItem(
    override val latLng: LatLng,
    override val time: Long = 0,
    override val distance: Float = 0f
) : RouteItemBase()