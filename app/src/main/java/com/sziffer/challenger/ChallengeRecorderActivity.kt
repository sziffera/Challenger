package com.sziffer.challenger

import android.Manifest
import android.annotation.SuppressLint
import android.app.ListActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
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
import com.sziffer.challenger.sensors.LeDeviceListAdapter
import com.sziffer.challenger.user.UserManager
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
    private lateinit var userManager: UserManager

    /** Bluetooth */
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled
    private var mScanning: Boolean = false

    private lateinit var leDeviceListAdapter: LeDeviceListAdapter
    private lateinit var bluetoothManager: BluetoothManager

    private val leScanCallback = BluetoothAdapter.LeScanCallback { device, _, _ ->
        runOnUiThread {
            leDeviceListAdapter.addDevice(device)
            leDeviceListAdapter.notifyDataSetChanged()
        }
    }

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

        //setting user preferences based on settings
        userManager = UserManager(this)
        if (userManager.autoPause) {
            autoPauseCheckBox.isChecked = true
            autoPause = true
        }
        if (userManager.preventScreenLock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        dbHelper = ChallengeDbHelper(this)
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

        setUpCadenceSensorButton.setOnClickListener {
            setUpCadenceSensor()
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

        muteVoiceCoachButton.setOnClickListener {
            muted = if (!muted) {
                muteVoiceCoachButton.setImageResource(R.drawable.ic_outline_mic_off_24)
                true
            } else {
                muteVoiceCoachButton.setImageResource(R.drawable.ic_settings_voice_24dp)
                false
            }

        }

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
            val builder: AlertDialog.Builder = AlertDialog.Builder(this, style.AlertDialogCustom)
            builder.setTitle(getString(R.string.finish_challenge))
                .setMessage(getString(R.string.finish_recording_message))
                .setCancelable(true)
                .setPositiveButton(
                    getString(R.string.yes)
                ) { _, _ -> finishChallenge() }
                .setNegativeButton(
                    getString(R.string.cancel)
                ) { dialog, _ -> dialog.dismiss() }

            val alert: AlertDialog = builder.create()
            alert.show()
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
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        if (checkPermissions()) {
            mMap.isMyLocationEnabled = true
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if (it != null) {
                    val latLng = LatLng(it.latitude, it.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.0f))
                }
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

        firstStartView.visibility = View.GONE
        countDownTextView.visibility = View.VISIBLE

        object : CountDownTimer(6000, 1000) {
            override fun onFinish() {
                Log.i(TAG, "CDT finished")

                countDownTextView.visibility = View.GONE

                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect
                            .createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    vibrator.vibrate(500)
                }

                if (autoPause) {
                    startStopButton.visibility = View.GONE
                } else
                    startStopButton.visibility = View.VISIBLE

                if (activityType == "running")
                    activityTypeImageView.setImageResource(R.drawable.running)

                recordingDataView.visibility = View.VISIBLE

                activityTypeImageView.visibility = View.VISIBLE
                durationTextView.visibility = View.VISIBLE
                challengeDataLinearLayout.visibility = View.VISIBLE
                finishButton.visibility = View.VISIBLE

                if (createdChallenge || challenge) {
                    differenceTextView.visibility = View.VISIBLE
                }
                if (isVoiceCoachEnabled)
                    muteVoiceCoachButton.visibility = View.GONE
                else
                    muteVoiceCoachButton.visibility = View.VISIBLE

                gpsService?.requestLocationUpdates()
                with(buttonSharedPreferences.edit()) {
                    putBoolean("started", true)
                    apply()
                }
            }

            override fun onTick(millisUntilFinished: Long) {
                countDownTextView.text = millisUntilFinished.div(1000).toInt().toString()
            }
        }.start()
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
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, style.AlertDialogCustom)
        builder.setTitle(getString(R.string.no_gps))
            .setIcon(R.drawable.ic_gps_off_24px)
            .setMessage(getString(R.string.no_gps_text))
            .setCancelable(true)
            .setNeutralButton(
                getString(R.string.ok)
            ) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }

        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun buildAlertMessageAirplaneIsOn() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, style.AlertDialogCustom)
        builder
            .setTitle(getString(R.string.airplane_is_on))
            .setIcon(R.drawable.ic_airplanemode_active_24px)
            .setMessage(getString(R.string.airplane_mode_text))
            .setCancelable(true)
            .setNeutralButton(
                getString(R.string.turn_off)
            ) { _, _ ->
                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            }

        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun buildAlertMessageNoLocationPoints() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, style.AlertDialogCustom)

        builder
            .setTitle(getString(R.string.already_finished))
            .setIcon(R.drawable.ic_notification_important_24px)
            .setMessage(getString(R.string.this_cannot_be_saved))
            .setCancelable(false)
            .setNeutralButton(
                getString(R.string.ok)
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

    private fun setUpCadenceSensor() {

        packageManager.takeIf {
            it.missingSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            )
        }?.also {
            Toast.makeText(
                this, R.string.ble_not_supported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

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
            firstStartView.visibility = View.GONE

            if (autoPause) {
                startStopButton.visibility = View.GONE
            } else {
                startStopButton.visibility = View.VISIBLE
            }
            finishButton.visibility = View.VISIBLE
        }
        if (!isVoiceCoachEnabled) {
            muteVoiceCoachButton.visibility = View.GONE
        } else {
            muteVoiceCoachButton.visibility = View.VISIBLE
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


    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

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
    private fun permissionRequest() {
        val locationApproved = ActivityCompat
            .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED


        if (!locationApproved) {
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

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {

            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(LocationUpdatesService.DISTANCE, 0.0f)
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0)

            mMap.addPolyline(
                PolylineOptions()
                    .addAll(gpsService?.route)
            )

            if (challenge || createdChallenge) {

                val difference = intent.getLongExtra(LocationUpdatesService.DIFFERENCE, 0).div(1000)
                when (difference.toInt()) {
                    0 -> {
                        differenceTextView.text =
                            DateUtils.formatElapsedTime(difference.absoluteValue)
                        differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                android.R.color.white
                            )
                        )
                    }
                    in 1..3600 -> {
                        differenceTextView.text = "+" + DateUtils.formatElapsedTime(difference)
                        differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                color.colorPlus
                            )
                        )
                    }
                    in -3600..0 -> {
                        differenceTextView.text =
                            "-" + DateUtils.formatElapsedTime(difference.absoluteValue)
                        differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                color.colorMinus
                            )
                        )
                    }
                    //the difference is too big, maybe there's a location error.
                    else -> {
                        differenceTextView.text =
                            getString(string.difference_error)
                        differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                android.R.color.white
                            )
                        )
                    }
                }
            }


            val avgDur = duration / 1000
            val avg = rawDistance / avgDur

            Log.i(TAG, "dist: $rawDistance, time: $duration")
            durationTextView.text = DateUtils.formatElapsedTime(duration / 1000)
            distanceTextView.text = "${getStringFromNumber(2, rawDistance / 1000)} km"
            maxSpeedTextView.text = "${gpsService?.maxSpeed?.times(3.6)?.let {
                getStringFromNumber(
                    1,
                    it
                )
            }} km/h"
            avgSpeedTextView.text = "${getStringFromNumber(1, avg.times(3.6))} km/h"

            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                val speed = location.speed * 3.6
                speedTextView.text = getStringFromNumber(1, speed) + " km/h"
            }
        }
    }

    private inner class DeviceScanActivity(
        private val bluetoothAdapter: BluetoothAdapter,
        private val handler: Handler
    ) : ListActivity() {

        private var mScanning: Boolean = false

        private fun scanLeDevice(enable: Boolean) {
            when (enable) {
                true -> {
                    // Stops scanning after a pre-defined scan period.
                    handler.postDelayed({
                        mScanning = false
                        bluetoothAdapter.stopLeScan(leScanCallback)
                    }, SCAN_PERIOD)
                    mScanning = true
                    bluetoothAdapter.startLeScan(leScanCallback)
                }
                else -> {
                    mScanning = false
                    bluetoothAdapter.stopLeScan(leScanCallback)
                }
            }
        }
    }

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_ENABLE_BT = 1232
        private const val SCAN_PERIOD: Long = 10000
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
        var muted: Boolean = false
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
