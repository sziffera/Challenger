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
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    SharedPreferences.OnSharedPreferenceChangeListener, AdapterView.OnItemSelectedListener {



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

        createdChallenge = intent.getBooleanExtra(CREATED_CHALLENGE_INTENT, false).also {
            Log.i(TAG, "$it is the created challenge bool")
        }

        if (createdChallenge) {
            distance = intent.getIntExtra(DISTANCE, 0).also {
                Log.i(TAG, "$it is the got distance")
            }
            avgSpeed = intent.getDoubleExtra(AVG_SPEED, 0.0).also {
                Log.i(TAG, "$it is the got avgSpeed")
            }
            avgSpeed = avgSpeed.div(3.6)

        } else {

            challenge = intent.getBooleanExtra(CHALLENGE, false).also {
                Log.i(TAG, "$it is the bool")
            }

            if (challenge) {
                recordedChallenge = intent.getParcelableExtra(RECORDED_CHALLENGE)
                val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
                val route =
                    Gson().fromJson<ArrayList<MyLocation>>(
                        recordedChallenge!!.routeAsString,
                        typeJson
                    )
                LocationUpdatesService.previousChallenge = route

            } else {
                this.differenceTextView.visibility = View.GONE
            }
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

        ArrayAdapter.createFromResource(
            this,
            R.array.activity_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            activitySelectorSpinner.adapter = adapter
        }
        activitySelectorSpinner.onItemSelectedListener = this



        if (!alreadyStarted) {
            startStopButton.visibility = View.GONE
            finishButton.visibility = View.GONE
            firstStartButton.visibility = View.VISIBLE
        } else {
            firstStartButton.visibility = View.GONE
            startStopButton.visibility = View.VISIBLE
            finishButton.visibility = View.VISIBLE
        }

        firstStartButton.setOnClickListener {

            gpsService?.requestLocationUpdates()


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
            finishChallenge()

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

    private fun finishChallenge() {
        val gson = Gson()
        val myLocationArrayString = gson.toJson(gpsService?.myRoute)

        gpsService?.finishAndSaveRoute()

        if (gpsService != null) {
            val duration: Long = gpsService!!.duration.div(1000)
            val distance = gpsService!!.distance.div(1000.0)
            val avg: Double = distance / duration.div(3600.0)
            val myIntent = Intent(this, ChallengeDetailsActivity::class.java)
                .putExtra(
                    ChallengeDetailsActivity.CHALLENGE_OBJECT,
                    Challenge(
                        "",
                        "",
                        selectedActivity,
                        distance,
                        gpsService!!.maxSpeed.times(3.6),
                        avg,
                        duration,
                        myLocationArrayString
                    ).also {
                        Log.i(TAG, "the sent challenge is: $it")
                    }
                )


            if (challenge) {
                with(myIntent) {
                    putExtra(ChallengeDetailsActivity.UPDATE, true)
                    //putExtra(ChallengeDetailsActivity.CHALLENGE_ID, challengeId)
                    putExtra(ChallengeDetailsActivity.PREVIOUS_CHALLENGE, recordedChallenge)
                }

            }
            with(buttonSharedPreferences.edit()) {
                putBoolean("started", false)
                commit()
            }
            startActivity(myIntent)
        }

        finish()
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

            if (challenge || createdChallenge) {
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

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        selectedActivity = parent?.getItemAtPosition(position).toString()
    }

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_CODE_BACKGROUND = 1545
        const val CHALLENGE = "challenge"
        const val RECORDED_CHALLENGE = "recorded"
        const val CREATED_CHALLENGE_INTENT = "createdChallenge"
        var createdChallenge: Boolean = false
            private set
        const val AVG_SPEED = "avgSpeed"
        const val DISTANCE = "distance"
        private val TAG = this::class.java.simpleName
        private lateinit var selectedActivity: String

        //indicates whether it is a simple recording or a challenge
        var challenge: Boolean = false
            private set
        var avgSpeed: Double = 0.0
            private set
        var distance: Int = 0
            private set
        //private var challengeId: String = ""
    }
}
