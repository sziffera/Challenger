package com.sziffer.challenger

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.LocationUpdatesService.LocalBinder
import com.sziffer.challenger.R.*
import kotlinx.android.synthetic.main.activity_challenge_recorder.*
import kotlin.math.absoluteValue


class ChallengeRecorderActivity : AppCompatActivity(), OnMapReadyCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mMap: GoogleMap
    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var finishButton: Button
    private lateinit var firstStartButton: Button
    private var recordedChallenge: Challenge? = null
    private lateinit var startStopButton: Button
    private lateinit var durationTextView: TextView
    private var gpsService: LocationUpdatesService? = null
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


        Log.i(
            TAG, "${ChallengeManager.isUpdate} is the update and isChallenge is" +
                    "${ChallengeManager.isChallenge} - should be false"
        )

        initChips()

        autoPauseCheckBox.setOnClickListener {
            val checkBox = it as CheckBox
            autoPause = checkBox.isChecked
            Log.i(TAG, "autopause is: $autoPause")
        }

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

                activityChooserChipGroup.visibility = View.GONE
                chooseAnActivity.visibility = View.GONE

                recordedChallenge = intent.getParcelableExtra(RECORDED_CHALLENGE)
                val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
                val route =
                    Gson().fromJson<ArrayList<MyLocation>>(
                        recordedChallenge!!.routeAsString,
                        typeJson
                    )
                activityType = recordedChallenge?.type
                LocationUpdatesService.previousChallenge = route

            } else {
                this.differenceTextView.visibility = View.GONE
            }
        }

        if (!checkPermissions()) {
            permissionRequest()
        }

        durationTextView = findViewById(id.recorderDurationTextView)
        durationTextView.visibility = View.GONE
        challengeDataLinearLayout.visibility = View.GONE
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
            firstStartButton.visibility = View.VISIBLE
        } else {
            firstStartButton.visibility = View.GONE
            autoPauseCheckBox.visibility = View.GONE
            chooseAnActivity.visibility = View.GONE
            activityChooserChipGroup.visibility = View.GONE
            if (autoPause) {
                startStopButton.visibility = View.GONE
            } else
                startStopButton.visibility = View.VISIBLE
            finishButton.visibility = View.VISIBLE
        }

        firstStartButton.setOnClickListener {


            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

            //checking conditions for location tracking
            when {
                isAirplaneModeOn(this) -> {
                    buildAlertMessageAirplaneIsOn()
                    return@setOnClickListener
                }
                !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                    buildAlertMessageNoGps()
                    return@setOnClickListener
                }
                activityType == null -> {
                    activityChooserChipGroup.startAnimation(
                        AnimationUtils.loadAnimation(
                            this,
                            R.anim.shake
                        )
                    )
                    return@setOnClickListener
                }
            }

            gpsService?.requestLocationUpdates()

            if (autoPause) {
                startStopButton.visibility = View.GONE
                val params = finishButton.layoutParams as LinearLayout.LayoutParams
                params.setMargins(12, 20, 12, 20)
                finishButton.layoutParams = params
            } else
                startStopButton.visibility = View.VISIBLE

            if (activityType == "running")
                activityTypeImageView.setImageResource(R.drawable.running)
            activityTypeImageView.visibility = View.VISIBLE
            chooseAnActivity.visibility = View.GONE
            durationTextView.visibility = View.VISIBLE
            challengeDataLinearLayout.visibility = View.VISIBLE
            it.visibility = View.GONE
            activityChooserChipGroup.visibility = View.GONE

            finishButton.visibility = View.VISIBLE
            autoPauseCheckBox.visibility = View.GONE

            if (createdChallenge || challenge) {
                differenceTextView.visibility = View.VISIBLE
            }

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
            if (it != null) {
                val latLng = LatLng(it.latitude, it.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))
            }
        }

        if (challenge) {

            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            val route =
                Gson().fromJson<ArrayList<MyLocation>>(recordedChallenge!!.routeAsString, typeJson)
            val mapPair = zoomAndRouteCreator(route)

            mMap.setOnMapLoadedCallback {
                mMap.addPolyline(PolylineOptions().addAll(mapPair.second))
                val padding = 50
                val cu = CameraUpdateFactory.newLatLngBounds(mapPair.first, padding)
                mMap.animateCamera(cu)
            }

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


        gpsService?.finishAndSaveRoute()
        autoPause = false
        with(buttonSharedPreferences.edit()) {
            putBoolean("started", false)
            commit()
        }

        Log.i(TAG, "activity type is $activityType")


        if (gpsService?.myRoute!!.size < 1) {
            buildAlertMessageNoLocationPoints()

        } else {

            if (gpsService != null) {
                val gson = Gson()
                val myLocationArrayString = gson.toJson(gpsService?.myRoute)
                val duration: Long = gpsService!!.durationHelper.div(1000)
                val distance = gpsService!!.distance.div(1000.0)
                val avg: Double = distance / duration.div(3600.0)
                val myIntent = Intent(this, ChallengeDetailsActivity::class.java)
                    .putExtra(
                        ChallengeDetailsActivity.CHALLENGE_OBJECT,
                        Challenge(
                            "",
                            "",
                            activityType.toString(),
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

                startActivity(myIntent)
            }

            finish()
        }
        activityType = null
    }


    private fun animateMarker(marker: Marker, location: Location) {
        val handler = Handler()
        val start: Long = SystemClock.uptimeMillis()
        val startLatLng: LatLng = marker.position
        val startRotation: Float = marker.rotation
        val duration: Long = 500
        val interpolator: Interpolator = LinearInterpolator()
        handler.post(object : Runnable {

            override fun run() {
                val elapsed: Long = SystemClock.uptimeMillis() - start
                val t: Float = interpolator.getInterpolation(
                    elapsed.toFloat()
                            / duration
                )
                val lng = t * location.longitude + (1 - t) * startLatLng.longitude
                val lat = t * location.latitude + (1 - t) * startLatLng.latitude
                val rotation = (t * location.bearing + (1 - t)
                        * startRotation)
                marker.position = LatLng(lat, lng)
                marker.rotation = rotation
                if (t < 1.0) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16)
                }
            }
        })
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

    private fun initChips() {
        with(cyclingChip) {
            isAllCaps = true
            textSize = 17f
        }
        with(runningChip) {
            isAllCaps = true
            textSize = 17f
        }
        activityChooserChipGroup.setOnCheckedChangeListener { _, checkedId ->
            activityType = activityChooserChipGroup.findViewById<Chip>(checkedId)?.text
        }
    }

    //region AlertMessages
    private fun buildAlertMessageNoGps() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("No GPS")
            .setIcon(R.drawable.ic_gps_off_24px)
            .setMessage("Your GPS seems to be disabled, please enable it")
            .setCancelable(true)
            .setNeutralButton(
                "Ok"
            ) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }

        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun buildAlertMessageAirplaneIsOn() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("Airplane mode is turned on")
            .setIcon(R.drawable.ic_airplanemode_active_24px)
            .setMessage("Unfortunately, you can't record an activity, if Airplane mode is turned on")
            .setCancelable(true)
            .setNeutralButton(
                "Turn off"
            ) { _, _ ->
                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            }

        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun buildAlertMessageNoLocationPoints() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)

        builder
            .setTitle("Already finished?")
            .setIcon(R.drawable.ic_notification_important_24px)
            .setMessage("Unfortunately, this activity can't be saved, but you can start it again if you would like to")
            .setCancelable(false)
            .setNeutralButton(
                "Ok"
            ) { dialog, _ ->
                clean()
                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
                dialog.dismiss()
            }


        val alert: AlertDialog = builder.create()
        alert.show()
    }
    //endregion AlertMessages


    private fun clean() {
        with(buttonSharedPreferences.edit()) {
            putBoolean("started", false)
            commit()
        }
        activityType = null
        challenge = false
    }

    //region permission requests
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
    //endregion permission requests

    private fun View.setMargins(
        left: Int? = null,
        top: Int? = null,
        right: Int? = null,
        bottom: Int? = null
    ) {
        val lp = layoutParams as? ViewGroup.MarginLayoutParams
            ?: return

        lp.setMargins(
            left ?: lp.leftMargin,
            top ?: lp.topMargin,
            right ?: lp.rightMargin,
            bottom ?: lp.rightMargin
        )

        layoutParams = lp
    }

    private inner class MyReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {

            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(LocationUpdatesService.DISTANCE, 0.0f)
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0)


            val avgSpeed = rawDistance.div(duration).also {
                Log.i(TAG, "the avg speed is: $it")
            }

            mMap.addPolyline(
                PolylineOptions()
                    .addAll(gpsService?.route)
                    .color(R.color.colorPlus)
            )

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
            distanceTextView.text = "${getStringFromNumber(2, rawDistance / 1000)} km"


            if (location != null) {

                //latLngBoundsBuilder.include(LatLng(location.latitude,location.longitude))
                //mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBoundsBuilder.build(),1000))
                val latLng = LatLng(location.latitude, location.longitude)

                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                val speed = location.speed * 3.6
                speedTextView.text = getStringFromNumber(1, speed) + " km/h"

            }
        }
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

        private var activityType: CharSequence? = null

        /** indicates whether it is a simple recording or a challenge */
        var challenge: Boolean = false
            private set
        var autoPause: Boolean = false
            private set
        var avgSpeed: Double = 0.0
            private set
        var distance: Int = 0
            private set
    }

}
