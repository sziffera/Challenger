package com.example.challenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.example.challenger.LocationUpdatesService.LocalBinder
import com.example.challenger.R.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson


class ChallengeRecorderActivity : AppCompatActivity(), OnMapReadyCallback,
SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_CODE_BACKGROUND = 1545
        private val TAG = this::class.java.simpleName
    }

    private lateinit var mMap: GoogleMap
    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var stopButton: Button
    private lateinit var startStopButton: Button
    private lateinit var durationTextView: TextView
    private var gpsService: LocationUpdatesService? = null
    private val tag = "RECORDER"
    private var mBound = false

    private val myReceiver: MyReceiver = MyReceiver()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            gpsService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            if (className.className == "com.example.challenger.LocationProviderService") {
                gpsService = null
                mBound = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_challenge_recorder)
        if (!checkPermissions()) {
            permissionRequest()
        }
        durationTextView = findViewById(id.recorderDurationTextView)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    override fun onStop() {
        if (mBound) {
            unbindService(serviceConnection)
            mBound = false
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)

        startStopButton = findViewById(id.startChallengeRecording)
        speedTextView = findViewById(id.challengeRecorderSpeedTextView)
        distanceTextView = findViewById(id.challengeRecorderDistanceTextView)
        //TODO(calculate elapsed time for chronometer)
        startStopButton.setOnClickListener {

            if (requestingLocationUpdates(this)) {
                gpsService?.removeLocationUpdates()

            } else {
                gpsService?.requestLocationUpdates()
            }
            setButtonState(requestingLocationUpdates(this))
        }
        stopButton = findViewById(id.stopRecording)
        stopButton.visibility = View.INVISIBLE
        stopButton.setOnClickListener {
            val gson = Gson()
            val stringJson = gson.toJson(gpsService?.route)

            gpsService?.finishAndSaveRoute()

            if (gpsService != null) {
                val duration: Long = gpsService!!.duration.div(1000)
                val distance = gpsService!!.distance.div(1000.0)
                val avg: Double = distance / duration.div(3600.0)
                startActivity(
                    Intent(this, ChallengeDetailsActivity::class.java)
                        .putExtra(
                            "challenge",
                            Challenge(
                                "",
                                "running",
                                "",
                                distance,
                                gpsService!!.maxSpeed.times(3.6),
                                avg,
                                duration,
                                stringJson
                            ).also {
                                Log.i(TAG, "the sent challenge is: $it")
                            }
                        )
                )
                finish()
            }

        }
        bindService(
            Intent(applicationContext, LocationUpdatesService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        setButtonState(requestingLocationUpdates(this))
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        mMap.isMyLocationEnabled = true

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener {
            val latLng = LatLng(it.latitude, it.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))
        }


    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            myReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver)
        super.onPause()
    }

    private fun setButtonState(requestingLocationUpdates: Boolean) {

        if (requestingLocationUpdates) {
            startStopButton.text = getString(string.pause)
            startStopButton.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.colorPause)
            stopButton.visibility = View.VISIBLE
        } else {
            startStopButton.text = getString(string.start)
            startStopButton.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.colorStart)
        }
    }

    private inner class MyReceiver : BroadcastReceiver() {
        //text does not depend on language
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {

            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(LocationUpdatesService.DISTANCE, 0.0f)
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0).also {
                Log.i("RECORDER", (it / 1000).toString())
            }

            val distance: String = "%.2f".format(rawDistance / 1000)
            val avgSpeed = rawDistance.div(duration).also {
                Log.i(TAG, "avg speed: $it")
            }
            durationTextView.text = DateUtils.formatElapsedTime(duration / 1000)
            distanceTextView.text = "$distance km"


            if (location != null) {

                Log.i(tag, location.toString())
                val latLng = LatLng(location.latitude, location.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                val speed = location.speed * 3.6
                speedTextView.text = "${"%.1f".format(speed)} km/h"

            }

        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key.equals(KEY_REQUESTING_LOCATION_UPDATES)) {
            val value = sharedPreferences.getBoolean(
                KEY_REQUESTING_LOCATION_UPDATES,
                false
            )
            setButtonState(value)
        }
    }

    //TODO(use just one permission request function)
    private fun permissionRequest() {
        val locationApproved = ActivityCompat
            .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (locationApproved) {
                val hasBackgroundLocationPermission = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasBackgroundLocationPermission) {
                    // handle location update
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_CODE_BACKGROUND
                    )
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ), REQUEST_CODE_BACKGROUND
                )
            }
        } else {
            // App doesn't have access to the device's location at all. Make full request
            // for permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    REQUEST
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    REQUEST
                )
            }
        }

    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

        } else {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }
}
