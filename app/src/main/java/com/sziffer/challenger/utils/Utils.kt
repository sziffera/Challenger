package com.sziffer.challenger.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.sziffer.challenger.R
import com.sziffer.challenger.model.MyLocation
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


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


fun sameMonth(date: Date): Boolean {
    val cal1 = Calendar.getInstance(Locale.GERMANY)
    val cal2 = Calendar.getInstance(Locale.GERMANY)
    cal1.time = Date()
    cal2.time = date
    return cal1[Calendar.MONTH] == cal2[Calendar.MONTH] &&
            cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
}

fun sameWeek(date: Date): Boolean {
    val cal1 = Calendar.getInstance(Locale.GERMANY)
    val cal2 = Calendar.getInstance(Locale.GERMANY)
    cal1.time = Date()
    cal2.time = date
    return cal1[Calendar.WEEK_OF_YEAR] == cal2[Calendar.WEEK_OF_YEAR] &&
            cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
}

fun sameYear(date: Date): Boolean {
    val cal1 = Calendar.getInstance(Locale.GERMANY)
    val cal2 = Calendar.getInstance(Locale.GERMANY)
    cal1.time = Date()
    cal2.time = date
    return cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
}

enum class UpdateTypes {
    WEATHER, DATA_SYNC
}

@SuppressLint("SimpleDateFormat")
fun shouldRefreshDataSet(type: UpdateTypes, minutes: Int, context: Context): Boolean {

    val lastRefreshSharedPreferences = context.getSharedPreferences(
        LAST_REFRESH,
        Context.MODE_PRIVATE
    )

    val lastRefreshString = if (type == UpdateTypes.DATA_SYNC) lastRefreshSharedPreferences
        .getString(LAST_REFRESH_TIME_SYNC, null)
    else
        lastRefreshSharedPreferences
            .getString(LAST_REFRESH_TIME_WEATHER, null)

    return if (lastRefreshString == null)
        true
    else {
        val lastRefreshDate: Date? = SimpleDateFormat("dd-MM-yyyy. HH:mm")
            .parse(lastRefreshString)
        val currentTime = Calendar.getInstance().time
        //difference in sec
        val difference = (currentTime.time - (lastRefreshDate?.time ?: 0))
            .div(1000).also {
                Log.i("MAIN", "$it is the difference")
            }
        difference > minutes * 10
    }
}

@SuppressLint("SimpleDateFormat")
fun updateRefreshDate(type: UpdateTypes, context: Context) {
    val lastRefreshSharedPreferences = context.getSharedPreferences(
        LAST_REFRESH,
        Context.MODE_PRIVATE
    )
    val currentDate: String
    currentDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
        current.format(formatter)

    } else {
        val date = Date();
        val formatter = SimpleDateFormat("dd-MM-yyyy. HH:mm")
        formatter.format(date)
    }
    with(lastRefreshSharedPreferences.edit()) {
        if (type == UpdateTypes.DATA_SYNC)
            putString(LAST_REFRESH_TIME_SYNC, currentDate)
        else
            putString(LAST_REFRESH_TIME_WEATHER, currentDate)
        apply()
    }
}


fun showDialog(
    title: String,
    text: String,
    buttonText: String,
    context: Context,
    layoutInflater: LayoutInflater,
    drawable: Drawable
) {

    Log.d("UTILS", "Dialog called")
    val dialogBuilder = AlertDialog.Builder(context, R.style.AlertDialog)
    val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
        findViewById<TextView>(R.id.dialogTitleTextView).text = title
        findViewById<TextView>(R.id.dialogDescriptionTextView).text = text
        findViewById<Button>(R.id.dialogOkButton).text = buttonText
    }
    dialogBuilder.setView(layoutView)

    val alertDialog = dialogBuilder.create().apply {
        window?.setGravity(Gravity.BOTTOM)
        window?.attributes?.windowAnimations = R.style.DialogAnimation
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        show()
    }

    layoutView.findViewById<Button>(R.id.dialogOkButton).setOnClickListener {
        alertDialog.dismiss()
    }
}

private const val LAST_REFRESH = "Utils.LastRefresh"
private const val LAST_REFRESH_TIME_SYNC = "time"
private const val LAST_REFRESH_TIME_WEATHER = "weatherTime"

