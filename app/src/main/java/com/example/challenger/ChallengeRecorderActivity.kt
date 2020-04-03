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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_challenge_recorder.*
import kotlin.math.absoluteValue


class ChallengeRecorderActivity : AppCompatActivity(), OnMapReadyCallback,
SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_CODE_BACKGROUND = 1545
        const val CHALLENGE = "challenge"
        const val RECORDED_CHALLENGE = "recorded"
        private val TAG = this::class.java.simpleName

        //indicates whether it is a simple recording or a challenge
        var challenge: Boolean = false
            private set
    }

    private lateinit var mMap: GoogleMap
    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var finishButton: Button
    private lateinit var firstStartButton: Button
    private var recordedChallenge: Challenge? = null
    private lateinit var startStopButton: Button
    private lateinit var durationTextView: TextView
    private var gpsService: LocationUpdatesService? = null
    private val latLngBoundsBuilder = LatLngBounds.builder()
    private lateinit var buttonSharedPreferences: SharedPreferences
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

        //get the bool which indicates whether it is a simple recorder or a challenger
        val intent = intent
        challenge = intent.getBooleanExtra(CHALLENGE, false).also {
            Log.i(TAG, "$it is the bool")
        }
        if (challenge) {
            recordedChallenge = intent.getParcelableExtra(RECORDED_CHALLENGE)
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            val route =
                Gson().fromJson<ArrayList<MyLocation>>(recordedChallenge!!.routeAsString, typeJson)
            LocationUpdatesService.previousChallenge = route.also {
                Log.i(TAG, "the route is: $route")
            }

        } else {
            this.differenceTextView.visibility = View.GONE
        }





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

        buttonSharedPreferences = getSharedPreferences("button", 0)

        val alreadyStarted = buttonSharedPreferences.getBoolean("started", false)

        startStopButton = findViewById(id.startChallengeRecording)
        finishButton = findViewById(id.stopRecording)
        firstStartButton = findViewById(R.id.firstStartButton)

        if (!alreadyStarted) {
            startStopButton.visibility = View.GONE
            finishButton.visibility = View.GONE
        } else
            firstStartButton.visibility = View.GONE


        firstStartButton.setOnClickListener {
            gpsService?.requestLocationUpdates()
            /*
            if (challenge) {
                Log.i(TAG,"challenging started")
                val serviceIntent = Intent(this,ChallengerService::class.java)
                serviceIntent.putExtra(ChallengerService.CHALLENGE_ROUTE,recordedChallenge?.routeAsString)
                serviceIntent.putExtra(ChallengerService.CHALLENGE_TIME,recordedChallenge?.dst)
                startService(serviceIntent)
            }
             */

            it.visibility = View.GONE
            startStopButton.visibility = View.VISIBLE
            finishButton.visibility = View.VISIBLE
            with(buttonSharedPreferences.edit()) {
                putBoolean("started", true)
                apply()
            }

        }

        speedTextView = findViewById(id.challengeRecorderSpeedTextView)
        distanceTextView = findViewById(id.challengeRecorderDistanceTextView)

        startStopButton.setOnClickListener {

            if (requestingLocationUpdates(this)) {
                gpsService?.removeLocationUpdates()

            } else {
                gpsService?.requestLocationUpdates()
            }
            setButtonState(requestingLocationUpdates(this))
        }



        finishButton.setOnClickListener {
            val gson = Gson()
            val stringJson = gson.toJson(gpsService?.route)
            val myLocationArrayString = gson.toJson(gpsService?.myRoute)

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
                                stringJson,
                                myLocationArrayString
                            ).also {
                                Log.i(TAG, "the sent challenge is: $it")
                            }
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                with(buttonSharedPreferences.edit()) {
                    putBoolean("started", false)
                    commit()
                }

            }
            finish()

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

        if (challenge) {
            //TODO(zoom to the route)
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
            finishButton.visibility = View.VISIBLE
        } else {
            startStopButton.text = getString(string.start)
        }
    }

    private inner class MyReceiver : BroadcastReceiver() {
        //text does not depend on language
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {

            //TODO(action to determine what is received)
/*
            if (intent.action == LocationUpdatesService.ACTION_DIFFERENCE_BROADCAST) {
                val diff = intent.getLongExtra(LocationUpdatesService.DIFFERENCE,0)
                differenceTextView.text = DateUtils.formatElapsedTime(diff)
                if(diff < 0) {
                    differenceTextView.setTextColor(Color.GREEN)
                } else
                    differenceTextView.setTextColor(Color.RED)
            }
*/
            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(LocationUpdatesService.DISTANCE, 0.0f)
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0)

            val distance: String = "%.2f".format(rawDistance / 1000)
            val avgSpeed = rawDistance.div(duration)

            if (challenge) {
                val difference = intent.getLongExtra(LocationUpdatesService.DIFFERENCE, 0).div(1000)
                if (difference < 0) {
                    differenceTextView.text =
                        "-" + DateUtils.formatElapsedTime(difference.absoluteValue)
                    differenceTextView.setTextColor(
                        ContextCompat.getColor(
                            this@ChallengeRecorderActivity,
                            color.colorMinus
                        )
                    )
                } else {
                    differenceTextView.text = "+" + DateUtils.formatElapsedTime(difference)
                    differenceTextView.setTextColor(
                        ContextCompat.getColor(
                            this@ChallengeRecorderActivity,
                            color.colorPlus
                        )
                    )
                }
            }

            durationTextView.text = DateUtils.formatElapsedTime(duration / 1000)
            distanceTextView.text = "$distance km"

            mMap.addPolyline(PolylineOptions().addAll(gpsService?.route))


            if (location != null) {

                //latLngBoundsBuilder.include(LatLng(location.latitude,location.longitude))
                //mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(),1000))
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
