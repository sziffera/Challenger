package com.sziffer.challenger.utils.extensions

import android.content.Context
import android.content.res.Resources
import android.location.Location
import android.util.Log
import android.view.View
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
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

fun Boolean.toVisibility(): Int {
    return if (this) View.VISIBLE else View.GONE
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

fun com.google.android.gms.maps.model.LatLng.asGeoLocation(): GeoLocation =
    GeoLocation(this.latitude, this.longitude)

fun Location.asGeoLocation(): GeoLocation = GeoLocation(this.latitude, this.longitude)

fun MyLocation.geoHash(): String =
    GeoFireUtils.getGeoHashForLocation(GeoLocation(this.latLng.latitude, this.latLng.longitude))

fun MyLatLng.geohash(): String =
    GeoFireUtils.getGeoHashForLocation(GeoLocation(this.latitude, this.longitude))

fun PublicChallenge.toHash(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "attempts" to attempts,
        "lat" to lat,
        "lng" to lng,
        "user_id" to userId,
        "geohash" to geoHash,
        "distance" to distance,
        "duration" to duration,
        "elevation_gained" to elevationGained,
        "elevation_loss" to elevationLoss,
        "type" to type,
        "timestamp" to timestamp,
        "route" to Gson().toJson(route)
    )
}

fun Challenge.toPublicChallengeHash(context: Context): Map<String, Any> {
    return this.toPublic(context).toHash()
}

fun Map<String, Any>.toPublicChallenge(): PublicChallenge {

    val challengeType =
        if (get("type") as String == ChallengeType.CYCLING.name) ChallengeType.CYCLING else ChallengeType.RUNNING

    val attempts: Long = if (get("attempts") == null) 1 else get("attempts") as Long

    return PublicChallenge(
        get("id") as String,
        attempts.toInt(),
        get("lat") as Double,
        get("lng") as Double,
        get("user_id") as String,
        get("geohash") as String,
        get("distance") as Double,
        get("duration") as Long,
        (get("elevation_gained") as Long).toInt(), //Int is stored as a Long (?)
        (get("elevation_loss") as Long).toInt(),
        challengeType,
        (get("timestamp") as Timestamp).toDate(),
        Gson().fromJson<ArrayList<PublicRouteItem>>
            (get("route") as String, Constants.publicRouteType)
    )
}

fun LatLng.geohash(): String =
    GeoFireUtils.getGeoHashForLocation(GeoLocation(this.latitude, this.longitude))

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
        0,
        startingPoint.latLng.latitude,
        startingPoint.latLng.longitude,
        FirebaseManager.mAuth.currentUser?.uid ?: "no_id",
        startingPoint.geoHash(),
        (this.dst * 1000).round(0),
        this.dur,
        this.elevGain,
        this.elevLoss,
        type,
        Constants.challengeDateFormat.parse(this.date) ?: Date(),
        reduceArrayLength(route, this.dst * 1000)
    )
}

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()