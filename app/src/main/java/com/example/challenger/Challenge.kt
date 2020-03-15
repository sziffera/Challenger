package com.example.challenger

import com.google.android.gms.maps.model.LatLng

data class Challenge (
    val name: String = "",
    val type: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val route: ArrayList<LatLng>? = null
)