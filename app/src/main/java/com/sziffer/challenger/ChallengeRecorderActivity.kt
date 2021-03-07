package com.sziffer.challenger

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
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
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
import com.sziffer.challenger.ble.LeDeviceListAdapter
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChallengeRecorderBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.user.UserManager
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.utils.dialogs.CustomListDialog
import com.sziffer.challenger.utils.dialogs.DataAdapter
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
    private var recordedChallenge: Challenge? = null
    private var gpsService: LocationUpdatesService? = null
    private lateinit var buttonSharedPreferences: SharedPreferences
    private lateinit var voiceCoachCustomDialog: CustomListDialog
    private var mBound = false
    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var userManager: UserManager
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var binding: ActivityChallengeRecorderBinding

    /** Bluetooth */
    private val bluetoothAdapter: BluetoothAdapter by lazy {
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

        binding = ActivityChallengeRecorderBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        mapFragment = supportFragmentManager
            .findFragmentById(id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        //setting user preferences based on settings
        userManager = UserManager(this)
        if (userManager.autoPause) {
            binding.autoPauseCheckBox.isChecked = true
            autoPause = true
        }
        if (userManager.preventScreenLock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        dbHelper = ChallengeDbHelper(this)
        initChips()
        initVoiceCoach()

        binding.recorderBottomNavigationView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.action_details -> {
                    Log.i("MENU", "DETAILS")
                    mapFragment.view?.visibility = View.GONE
                    binding.detailsLinearLayout.visibility = View.VISIBLE
                    true
                }
                R.id.action_map -> {
                    binding.detailsLinearLayout.visibility = View.GONE
                    mapFragment.view?.visibility = View.VISIBLE
                    Log.i("MENU", "MAP")
                    true
                }
                else -> true
            }
        }

        binding.autoPauseCheckBox.setOnClickListener {
            val checkBox = it as CheckBox
            autoPause = checkBox.isChecked
            Log.i(TAG, "autopause is: $autoPause")
        }
        binding.muteVoiceCoachButton.setOnClickListener {
            muted = if (!muted) {
                binding.muteVoiceCoachButton.setImageResource(R.drawable.ic_outline_mic_off_24)
                true
            } else {
                binding.muteVoiceCoachButton.setImageResource(R.drawable.ic_settings_voice_24dp)
                false
            }

        }
        createdChallenge = intent.getBooleanExtra(CREATED_CHALLENGE_INTENT, false).also {
            Log.i(TAG, "$it is the created challenge bool")
        }

        binding.setUpCadenceSensorButton.setOnClickListener {
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

                binding.activityChooserChipGroup.visibility = View.GONE
                binding.chooseAnActivity.visibility = View.GONE

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
                this.binding.differenceTextView.visibility = View.GONE
            }
        }

        if (!locationPermissionCheck(this)) {
            locationPermissionRequest(this, this)
        }

        binding.recorderDurationTextView.visibility = View.GONE


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

        updateView(alreadyStarted)

        binding.firstStartButton.setOnClickListener {
            firstStartButtonOnClick()
        }


        binding.startStopChallengeRecording.setOnClickListener {

            if (requestingLocationUpdates(this)) {
                gpsService?.removeLocationUpdates()

            } else {
                gpsService?.requestLocationUpdates()
            }
            setButtonState(requestingLocationUpdates(this))
        }

        binding.stopRecording.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog
                .Builder(this, style.AlertDialogCustom)
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

        if (locationPermissionCheck(this)) {
            with(mMap) {
                isMyLocationEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isZoomControlsEnabled = true
            }
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
                val currentDate: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val current = LocalDateTime.now()
                    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
                    current.format(formatter)

                } else {
                    val date = Date()
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
                binding.activityChooserChipGroup.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.shake
                    )
                )
                return
            }
        }

        binding.firstStartView.visibility = View.GONE
        binding.countDownTextView.visibility = View.VISIBLE

        object : CountDownTimer(6000, 1000) {
            override fun onFinish() {
                Log.i(TAG, "CDT finished")

                binding.countDownTextView.visibility = View.GONE

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
                    binding.startStopChallengeRecording.visibility = View.GONE
                    binding.startStopSpace.visibility = View.GONE
                } else
                    binding.startStopChallengeRecording.visibility = View.VISIBLE

                if (activityType == "running")
                    binding.activityTypeImageView.setImageResource(R.drawable.running)

                binding.recordingDataView.visibility = View.VISIBLE

                binding.activityTypeImageView.visibility = View.VISIBLE
                binding.recorderDurationTextView.visibility = View.VISIBLE
                //challengeDataLinearLayout.visibility = View.VISIBLE
                binding.stopRecording.visibility = View.VISIBLE

                if (createdChallenge || challenge) {
                    binding.differenceTextView.visibility = View.VISIBLE
                }
                if (isVoiceCoachEnabled)
                    binding.muteVoiceCoachButton.visibility = View.VISIBLE
                else
                    binding.muteVoiceCoachButton.visibility = View.GONE

                binding.recorderBottomNavigationView.visibility = View.VISIBLE
                mapFragment.view?.visibility = View.GONE

                gpsService?.requestLocationUpdates()
                with(buttonSharedPreferences.edit()) {
                    putBoolean("started", true)
                    apply()
                }
                gpsService?.sayRecordingStarted()
            }

            override fun onTick(millisUntilFinished: Long) {
                binding.countDownTextView.text = millisUntilFinished.div(1000).toInt().toString()
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
        binding.voiceCoachSetUpButton.setOnClickListener {
            voiceCoachCustomDialog.show()
            voiceCoachCustomDialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun initChips() {
        with(binding.cyclingChip) {
            isAllCaps = true
            textSize = 17f
        }
        with(binding.runningChip) {
            isAllCaps = true
            textSize = 17f
        }
        binding.activityChooserChipGroup.setOnCheckedChangeListener { _, checkedId ->
            activityType = binding.activityChooserChipGroup.findViewById<Chip>(checkedId)?.text
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

    inline fun FragmentManager.inTransaction(func: FragmentTransaction.() -> Unit) {
        val fragmentTransaction = beginTransaction()
        fragmentTransaction.func()
        fragmentTransaction.commit()
    }

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
            binding.startStopChallengeRecording.visibility = View.GONE
            binding.stopRecording.visibility = View.GONE
            binding.firstStartButton.visibility = View.VISIBLE
        } else {
            binding.firstStartView.visibility = View.GONE

            if (autoPause) {
                binding.startStopChallengeRecording.visibility = View.GONE
            } else {
                binding.startStopChallengeRecording.visibility = View.VISIBLE
            }
            binding.stopRecording.visibility = View.VISIBLE
        }
        if (!isVoiceCoachEnabled) {
            binding.muteVoiceCoachButton.visibility = View.GONE
        } else {
            binding.muteVoiceCoachButton.visibility = View.VISIBLE
        }
    }

    private fun setButtonState(requestingLocationUpdates: Boolean) {

        if (requestingLocationUpdates) {
            binding.startStopChallengeRecording.text = getString(string.pause)
            binding.stopRecording.visibility = View.VISIBLE
        } else {
            binding.startStopChallengeRecording.text = getString(string.start)
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

    private inner class ActivityDataReceiver : BroadcastReceiver() {

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {

            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            val rawDistance: Float = intent.getFloatExtra(
                LocationUpdatesService.DISTANCE,
                0.0f
            )
            val duration: Long = intent.getLongExtra(LocationUpdatesService.DURATION, 0)
            val autoPauseActive = intent.getBooleanExtra(
                LocationUpdatesService.AUTO_PAUSE_ACTIVE,
                false
            ).also {
                Log.d(TAG, "the auto pause bool is: $it")
            }

            val altitude = intent.getIntExtra(LocationUpdatesService.ALTITUDE, 0)
            val elevationGained = intent
                .getIntExtra(LocationUpdatesService.ELEVATION_GAINED, 0)

            binding.altitudeTextView.text = "${altitude}m"


            mMap.addPolyline(
                PolylineOptions()
                    .color(R.color.colorPrimaryDark)
                    .addAll(gpsService?.route)

            )

            if (challenge || createdChallenge) {

                val difference = intent.getLongExtra(
                    LocationUpdatesService.DIFFERENCE,
                    0
                ).div(1000)
                when (difference.toInt()) {
                    0 -> {
                        binding.differenceTextView.text =
                            DateUtils.formatElapsedTime(difference.absoluteValue)
                        binding.differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                android.R.color.white
                            )
                        )
                    }
                    in 1..3600 -> {
                        binding.differenceTextView.text =
                            "+" + DateUtils.formatElapsedTime(difference)
                        binding.differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                color.colorPlus
                            )
                        )
                    }
                    in -3600..0 -> {
                        binding.differenceTextView.text =
                            "-" + DateUtils.formatElapsedTime(difference.absoluteValue)
                        binding.differenceTextView.setTextColor(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                color.colorMinus
                            )
                        )
                    }
                    //the difference is too big, maybe there's a location error.
                    else -> {
                        binding.differenceTextView.text =
                            getString(string.difference_error)
                        binding.differenceTextView.setTextColor(
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

            binding.recorderDurationTextView.text = DateUtils.formatElapsedTime(duration / 1000)
            binding.recorderDurationTextView.text = "${
                getStringFromNumber(
                    2,
                    rawDistance / 1000
                )
            } km"
            binding.maxSpeedTextView.text = "${
                gpsService?.maxSpeed?.times(3.6)?.let {
                    getStringFromNumber(
                        1,
                        it
                    )
                }
            } km/h"
            binding.avgSpeedTextView.text = "${
                getStringFromNumber(
                    1,
                    avg.times(3.6)
                )
            } km/h"

            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                if (autoPauseActive) {
                    binding.challengeRecorderSpeedTextView.text = "0 km/h"
                    Log.d(TAG, "setting the speed to zero")
                } else {
                    val speed = location.speed * 3.6
                    binding.challengeRecorderSpeedTextView.text =
                        getStringFromNumber(1, speed) + " km/h"
                }
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
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
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
