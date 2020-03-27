package com.example.challenger

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager

const val KEY_REQUESTING_LOCATION_UPDATES = "requestingLocationUpdates"

fun requestingLocationUpdates(context: Context?): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
}

fun setRequestingLocationUpdates(
    context: Context?,
    requestingLocationUpdates: Boolean
) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
        .apply()
}

fun getLocationText(location: Location?): CharSequence {
    return if (location == null) "Unknown location" else "(" + location.latitude + ", " + location.longitude + ")"
}

fun getLocationTitle(context: Context): String? {
    return  "hehe"
}

fun getStringFromNumber(floatingPoint: Int, value: Number): String {
    return "%.${floatingPoint}f".format(value)
}


