package com.sziffer.challenger.utils

import java.text.SimpleDateFormat

object Constants {

    val challengeDateFormat = SimpleDateFormat("dd-MM-yyyy. HH:mm")
    const val KEY_WEATHER = "Weather"
    const val KEY_WEATHER_DATA = "WeatherData"

    const val WINDOW_SIZE_HELPER = 50
    const val MAX_WINDOW_SIZE = 130
    const val SMOOTH_MODE = "triangular"

    const val MIN_ROUTE_SIZE = WINDOW_SIZE_HELPER
}