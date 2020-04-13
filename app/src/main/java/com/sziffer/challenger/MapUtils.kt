package com.sziffer.challenger

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds


fun zoomAndRouteCreator(locations: ArrayList<MyLocation>): Pair<LatLngBounds, ArrayList<LatLng>> {
    val latLng: ArrayList<LatLng> = ArrayList()
    val builder = LatLngBounds.builder()
    for (item in locations) {
        builder.include(item.latLng)
        latLng.add(item.latLng)
    }
    return Pair(builder.build(), latLng)
}