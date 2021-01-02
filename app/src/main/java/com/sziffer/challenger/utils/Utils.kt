package com.sziffer.challenger.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.sziffer.challenger.model.MyLocation


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

fun getStringFromNumber(floatingPoint: Int, value: Number): String {
    return "%.${floatingPoint}f".format(value)
}

fun String.isEmailAddressValid(): Boolean {
    return this.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun isAirplaneModeOn(context: Context): Boolean {
    return Settings.System.getInt(
        context.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON, 0
    ) != 0
}

fun zoomAndRouteCreator(locations: ArrayList<MyLocation>): Pair<LatLngBounds, ArrayList<LatLng>> {
    val latLng: ArrayList<LatLng> = ArrayList()
    val builder = LatLngBounds.builder()
    for (item in locations) {
        builder.include(item.latLng)
        latLng.add(item.latLng)
    }
    return Pair(builder.build(), latLng)
}

fun takeScreenshot(view: View): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}

fun promptEnableBluetooth(bluetoothAdapter: BluetoothAdapter, activity: Activity) {
    if (!bluetoothAdapter.isEnabled) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(activity, enableBtIntent, 1, null)
    }
}

// region location request
fun locationPermissionRequest(context: Context, activity: Activity) {
    val locationApproved = ActivityCompat
        .checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED


    if (!locationApproved) {

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            LOCATION_REQUEST
        )
    }

}

fun locationPermissionCheck(context: Context): Boolean {

    return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

}

private const val LOCATION_REQUEST = 1234
// endregion location request