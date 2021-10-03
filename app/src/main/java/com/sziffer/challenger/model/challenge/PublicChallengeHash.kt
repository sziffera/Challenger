package com.sziffer.challenger.model.challenge

import com.google.gson.Gson
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.extensions.geohash

class PublicChallengeHash(val publicChallenge: PublicChallenge) : HashMap<String, Any>() {

    // needed by Firestore
    constructor() : this(PublicChallenge())

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

    var challenge: String
        get() = this[KEY_CHALLENGE] as String
        set(value) {
            put(KEY_CHALLENGE, value)
        }

    init {
        put(KEY_CHALLENGE, Gson().toJson(publicChallenge))
        put(KEY_LAT, publicChallenge.route?.first()?.latLng?.latitude ?: 0.0)
        put(KEY_LNG, publicChallenge.route?.first()?.latLng?.longitude ?: 0.0)
        put(KEY_GEOHASH, publicChallenge.route?.first()?.latLng?.geohash() ?: "")
    }

    val getPublicChallenge: PublicChallenge
        get() {
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