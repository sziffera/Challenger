package com.sziffer.challenger.processing

import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

interface DataProcessCompletionListener {
    fun completed(
        polylineOptions: PolylineOptions,
        elevationGained: Double,
        elevationLoss: Double,
        builder: LatLngBounds.Builder
    )
}