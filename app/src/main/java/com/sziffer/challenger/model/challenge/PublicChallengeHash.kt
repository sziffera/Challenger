package com.sziffer.challenger.model.challenge

import android.util.Log
import com.google.gson.Gson
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.extensions.geohash

/**
 * The Geo query from Firestore only works with HashMaps
 * This class holds the PublicChallenge as a JSON String and the fields
 * needed for geo query (lat,lng,geohash)
 */
class PublicChallengeHash : HashMap<String, Any>() {

    var lng: Double
        get() = this[KEY_LNG] as Double
        set(value) {
            put(KEY_LNG, value)
        }

    var lat: Double
        get() = this[KEY_LAT] as Double
        set(value) {
            put(KEY_LAT, value)
        }

    var geohash: String
        get() = this[KEY_GEOHASH] as String
        set(value) {
            put(KEY_GEOHASH, value)
        }

    // storing the PublicChallenge as a JSON String
    var challenge: String
        get() = this[KEY_CHALLENGE] as String
        set(value) {
            put(KEY_CHALLENGE, value)
        }


    fun setFields(publicChallenge: PublicChallenge) {
        put(KEY_CHALLENGE, Gson().toJson(publicChallenge))
        put(KEY_LAT, publicChallenge.route?.first()?.latLng?.latitude ?: 0.0)
        put(KEY_LNG, publicChallenge.route?.first()?.latLng?.longitude ?: 0.0)
        put(KEY_GEOHASH, publicChallenge.route?.first()?.latLng?.geohash() ?: "")

    }

    // getter for the PublicChallenge using GSON
    val getPublicChallenge: PublicChallenge
        get() {
            Log.d("PUBLIC_CHALLENGE_HASH", "getting the json data")
            return Gson().fromJson(
                this[KEY_CHALLENGE] as String,
                Constants.publicChallengeType
            )
        }

    companion object {
        private const val KEY_CHALLENGE = "challenge"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_GEOHASH = "geohash"
    }
}