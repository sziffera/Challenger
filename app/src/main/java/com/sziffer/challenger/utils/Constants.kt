package com.sziffer.challenger.utils

import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.model.challenge.MyLocation
import com.sziffer.challenger.model.challenge.PublicChallengeHash
import com.sziffer.challenger.model.challenge.PublicRouteItem
import java.text.SimpleDateFormat

object Constants {

    val challengeDateFormat = SimpleDateFormat("dd-MM-yyyy. HH:mm")
    const val KEY_WEATHER = "Weather"
    const val KEY_WEATHER_DATA = "WeatherData"

    val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
    val publicRouteType = object : TypeToken<ArrayList<PublicRouteItem>>() {}.type
    val publicChallengeType = object : TypeToken<PublicChallenge>() {}.type
    val publicChallengeHashType = object : TypeToken<PublicChallengeHash>() {}.type

    const val WINDOW_SIZE_HELPER = 40
    const val MAX_WINDOW_SIZE = 130
    const val SMOOTH_MODE = "triangular"

    const val MIN_ROUTE_SIZE = WINDOW_SIZE_HELPER

    object PublicChallenge {
        const val RADIUS_UPLOAD_NEARBY = 5000.0 // in metres
        const val RADIUS_NEARBY_CHALLENGES = 150000.0 // in metres
    }

    object Database {
        const val PUBLIC_CHALLENGES_TABLE_NAME = "public_challenges"
        const val PUBLIC_ROUTES_TABLE_NAME = "public_routes"
    }
}