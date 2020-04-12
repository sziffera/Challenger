package com.sziffer.challenger

import android.content.Context
import android.location.Location
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

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

fun getStringFromNumber(floatingPoint: Int, value: Number): String {
    return "%.${floatingPoint}f".format(value)
}

fun String.isEmailAddressValid(): Boolean {
    return this.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun zoomToRoute(items: ArrayList<LatLng>): LatLngBounds {
    val builder = LatLngBounds.builder()
    for (i in items) {
        builder.include(i)
    }
    return builder.build()
}

fun isAirplaneModeOn(context: Context): Boolean {
    return Settings.System.getInt(
        context.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON, 0
    ) != 0;
}


