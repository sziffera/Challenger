package com.sziffer.challenger

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
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
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.LocationUpdatesService.LocalBinder
import com.sziffer.challenger.R.*
import com.sziffer.challenger.dialogs.CustomListDialog
import com.sziffer.challenger.dialogs.DataAdapter
import com.sziffer.challenger.user.FirebaseManager
import kotlinx.android.synthetic.main.activity_challenge_recorder.*
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue


class ChallengeRecorderActivity : AppCompatActivity(), OnMapReadyCallback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DataAdapter.RecyclerViewItemClickListener {

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
    private lateinit var voiceCoachCustomDialog: CustomListDialog
    private var mBound = false
    private lateinit var dbHelper: ChallengeDbHelper

    private val activityDataReceiver: ActivityDataReceiver = ActivityDataReceiver()

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

    //region activity lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_challenge_recorder)

        dbHelper = ChallengeDbHelper(this)

        //TODO(not finished)
        Log.i(
            TAG, "${ChallengeManager.isUpdate} is the update and isChallenge is" +
                    "${ChallengeManager.isChallenge} - should be false"
        )

        initChips()
        initVoiceCoach()

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

                val recordedChallengeId = intent.getIntExtra(RECORDED_CHALLENGE_ID, -1)
                recordedChallenge = dbHelper.getChallenge(recordedChallengeId)

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

        updateView(alreadyStarted)

        firstStartButton.setOnClickListener {
            firstStartButtonOnClick()
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

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            activityDataReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(activityDataReceiver)
        super.onPause()
    }

    //endregion activity lifecycle


    //region map
    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        mMap.isMyLocationEnabled = true

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener {
            if (it != null) {
                val latLng = LatLng(it.latitude, it.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.0f))
            }
        }

        //if it is a recorded activity challenge, draws the route onto the map
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
    //endregion map


    //region recording actions
    private fun finishChallenge() {

        gpsService?.finishAndSaveRoute()
        autoPause = false
        with(buttonSharedPreferences.edit()) {
            putBoolean("started", false)
            commit()
        }

        isVoiceCoachEnabled = false
        numberForVoiceCoach = 0

        Log.i(TAG, "activity type is $activityType")


        if (gpsService?.myRoute!!.size < 1) {
            buildAlertMessageNoLocationPoints()

        } else {

            if (gpsService != null) {

                //--------DEBUG DATA-------
                val key = UUID.randomUUID()
                val debugData = Gson().toJson(gpsService?.debugList)
                FirebaseManager.currentUserRef!!.child("debug")
                    .child(key.toString()).setValue(debugData)

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

                val gson = Gson()
                val myLocationArrayString = gson.toJson(gpsService?.myRoute)
                val duration: Long = gpsService!!.durationHelper.div(1000)
                val distance = gpsService!!.distance.div(1000.0)
                val avg: Double = distance / duration.div(3600.0)

                val dbHelper = ChallengeDbHelper(this)
                val newChallenge = Challenge(
                    "",
                    UUID.randomUUID().toString(),
                    currentDate,
                    "",
                    activityType.toString(),
                    distance,
                    gpsService!!.maxSpeed.times(3.6),
                    avg,
                    duration,
                    myLocationArrayString
                )
                val challengeId = dbHelper.addChallenge(newChallenge).also {
                    Log.i(TAG, "the id for the new challenge is: $it")
                }

                val myIntent = Intent(this, ChallengeDetailsActivity::class.java)
                    .putExtra(
                        ChallengeDetailsActivity.CHALLENGE_ID,
                        challengeId.also {
                            Log.i(TAG, "the sent challenge is: $it")
                        }
                    )

                if (challenge) {
                    with(myIntent) {
                        putExtra(ChallengeDetailsActivity.UPDATE, true)
                        putExtra(
                            ChallengeDetailsActivity.PREVIOUS_CHALLENGE_ID,
                            recordedChallenge!!.id.toLong()
                        )
                    }
                }
                dbHelper.close()
                startActivity(myIntent)
            }
            finish()
        }
        activityType = null
    }

    private fun firstStartButtonOnClick() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        //checking conditions for location tracking
        when {
            isAirplaneModeOn(this) -> {
                buildAlertMessageAirplaneIsOn()
                return
            }
            !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                buildAlertMessageNoGps()
                return
            }
            activityType == null -> {
                activityChooserChipGroup.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.shake
                    )
                )
                return
            }
        }

        gpsService?.requestLocationUpdates()

        //TODO(Layout is ugly + clean code)
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
        voiceCoachSetUpButton.visibility = View.GONE
        durationTextView.visibility = View.VISIBLE
        challengeDataLinearLayout.visibility = View.VISIBLE
        firstStartButton.visibility = View.GONE
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
    //endregion recording actions


    //region init
    private fun initVoiceCoach() {
        val res = resources.getStringArray(R.array.voice_coach_items)
        val array: ArrayList<String> = ArrayList()
        array.addAll(res)
        array.add(getString(R.string.turn_off))
        val dataAdapter = DataAdapter(array, this)
        voiceCoachCustomDialog = CustomListDialog(this, dataAdapter)
        initVoiceCoachButton()
    }

    private fun initVoiceCoachButton() {
        voiceCoachSetUpButton.setOnClickListener {
            voiceCoachCustomDialog.show()
            voiceCoachCustomDialog.setCanceledOnTouchOutside(false)
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
    //endregion init

    //region Dialogs
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
    //endregion Dialog


    //region helper methods

    override fun onBackPressed() {
        if (buttonSharedPreferences.getBoolean("started", false)) {
            this.moveTaskToBack(true)
        } else
            super.onBackPressed()
    }

    private fun updateView(alreadyStarted: Boolean) {
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
    }

    private fun setButtonState(requestingLocationUpdates: Boolean) {

        if (requestingLocationUpdates) {
            startStopButton.text = getString(string.pause)
            finishButton.visibility = View.VISIBLE
        } else {
            startStopButton.text = getString(string.start)
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


    /** calculates and stores the number for the voice coach */
    override fun clickOnVoiceCoachItem(data: String) {

        when {
            data.contains("km") -> {
                //in metres
                numberForVoiceCoach = data.replace("[^0-9]".toRegex(), "").toInt().times(1000)
                voiceCoachIsBasedOnDistance = true
            }
            data.toLowerCase(Locale.ROOT)
                .contains(getString(string.minute).toLowerCase(Locale.ROOT)) -> {
                //in seconds
                numberForVoiceCoach = data.replace("[^0-9]".toRegex(), "").toInt().times(60)
                voiceCoachIsBasedOnDuration = true
            }
            else -> {
                numberForVoiceCoach = 0
                isVoiceCoachEnabled = false
            }

        }

        Log.i(TAG, numberForVoiceCoach.toString())
        voiceCoachCustomDialog.dismiss()
    }

    /** restores the affected values when leaving activity */
    private fun clean() {
        with(buttonSharedPreferences.edit()) {
            putBoolean("started", false)
            commit()
        }
        numberForVoiceCoach = 0
        isVoiceCoachEnabled = false
        activityType = null
        challenge = false
    }
    //endregion helper methods

    //region permission requests
    //TODO(use just one permission request function, strange error on pixel after first request)
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

    private inner class ActivityDataReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {

            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(LocationUpdatesService.DISTANCE, 0.0f)
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0)


            val avgSpeed = rawDistance.div(duration)

            mMap.addPolyline(
                PolylineOptions()
                    .addAll(gpsService?.route)
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
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

                val speed = location.speed * 3.6
                speedTextView.text = getStringFromNumber(1, speed) + " km/h"

            }
        }
    }

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_CODE_BACKGROUND = 1545
        const val CHALLENGE = "challenge"
        const val RECORDED_CHALLENGE_ID = "recorded"
        const val CREATED_CHALLENGE_INTENT = "createdChallenge"
        var createdChallenge: Boolean = false
            private set
        const val AVG_SPEED = "avgSpeed"
        const val DISTANCE = "distance"
        private val TAG = ChallengeRecorderActivity::class.java.simpleName

        var activityType: CharSequence? = null
            private set

        /** indicates whether it is a simple recording or a challenge */
        var challenge: Boolean = false
            private set
        var autoPause: Boolean = false
            private set
        var avgSpeed: Double = 0.0
            private set
        var distance: Int = 0
            private set

        /** stores whether the voice coach is enabled or not */
        var isVoiceCoachEnabled: Boolean = false
            private set(value) {
                if (!value) {
                    voiceCoachIsBasedOnDuration = false
                    voiceCoachIsBasedOnDistance = false
                }
                field = value
            }
        var voiceCoachIsBasedOnDistance: Boolean = false
            private set(value) {
                if (value) {
                    voiceCoachIsBasedOnDuration = false
                    isVoiceCoachEnabled = true
                }
                field = value
            }
        var voiceCoachIsBasedOnDuration: Boolean = false
            private set(value) {
                if (value) {
                    voiceCoachIsBasedOnDistance = false
                    isVoiceCoachEnabled = true
                }
                field = value
            }
        var numberForVoiceCoach: Int = 0
            private set
    }

}
