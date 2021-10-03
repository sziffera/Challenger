package com.sziffer.challenger.utils.extensions

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.gson.Gson
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.model.challenge.*
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.utils.reduceArrayLength
import java.util.*
import kotlin.math.round

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun Float.round(decimals: Int): Float {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return (round(this * multiplier) / multiplier).toFloat()
}

fun Double.popToPercent(): String {
    return "${getStringFromNumber(0, this.times(100))}%"
}

fun Number.toPace(): String {
    val minutes = this.toDouble() / 60 % 60
    val seconds = this.toDouble() % 60
    Log.d("PACE", "${minutes.toInt()}:${seconds.toInt()}")
    val secondsFormatted = if (seconds > 9)
        seconds.toInt().toString()
    else
        "0${seconds.toInt()}"
    return "${minutes.toInt()}:$secondsFormatted"
}

fun MyLocation.geoHash(): String =
    GeoFireUtils.getGeoHashForLocation(GeoLocation(this.latLng.latitude, this.latLng.longitude))

fun MyLatLng.geohash(): String =
    GeoFireUtils.getGeoHashForLocation(GeoLocation(this.latitude, this.longitude))

fun MyLatLng.toGoogleLatLng(): com.google.android.gms.maps.model.LatLng =
    com.google.android.gms.maps.model.LatLng(this.latitude, this.longitude)

fun Challenge.toPublic(context: Context): PublicChallenge {
    val route =
        Gson().fromJson<ArrayList<MyLocation>>(this.routeAsString, Constants.typeJson)
    val startingPoint = route.first()
    val type =
        if (this.type == context.getString(R.string.running))
            ChallengeType.RUNNING
        else ChallengeType.CYCLING

    return PublicChallenge(
        this.firebaseId,
        startingPoint.latLng.latitude,
        startingPoint.latLng.longitude,
        FirebaseManager.mAuth.currentUser?.uid ?: "no_id",
        startingPoint.geoHash(),
        (this.dst * 1000).round(0),
        this.dur,
        this.elevGain,
        type,
        Constants.challengeDateFormat.parse(this.date) ?: Date(),
        reduceArrayLength(route, this.dst * 1000)
    )
}

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()