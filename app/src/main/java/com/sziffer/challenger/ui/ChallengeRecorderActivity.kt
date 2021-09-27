package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.text.format.DateUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.sziffer.challenger.R
import com.sziffer.challenger.R.*
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChallengeRecorderBinding
import com.sziffer.challenger.model.challenge.Challenge
import com.sziffer.challenger.model.challenge.MyLocation
import com.sziffer.challenger.model.user.UserManager
import com.sziffer.challenger.services.LocationUpdatesService
import com.sziffer.challenger.services.LocationUpdatesService.LocalBinder
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.utils.dialogs.CustomListDialog
import com.sziffer.challenger.utils.dialogs.DataAdapter
import com.welie.blessed.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


class ChallengeRecorderActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    DataAdapter.RecyclerViewItemClickListener {


    private var recordedChallenge: Challenge? = null
    private var gpsService: LocationUpdatesService? = null
    private lateinit var buttonSharedPreferences: SharedPreferences
    private lateinit var voiceCoachCustomDialog: CustomListDialog
    private var mBound = false
    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var userManager: UserManager

    private var mapBox: MapboxMap? = null
    private var style: Style? = null

    private lateinit var binding: ActivityChallengeRecorderBinding

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
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

        Mapbox.getInstance(this, MAPBOX_ACCESS_TOKEN)

        binding = ActivityChallengeRecorderBinding.inflate(layoutInflater)

        binding.recenterButton.visibility = View.INVISIBLE


        binding.recenterButton.setOnClickListener {
            binding.recenterButton.visibility = View.INVISIBLE
            startLocationTracking()
        }

        setContentView(binding.root)

        this.actionBar?.hide()

        binding.mapbox.onCreate(savedInstanceState)
        binding.mapbox.getMapAsync { mapBox ->
            this.mapBox = mapBox
            mapBox.setStyle(Style.OUTDOORS) {
                styleLoaded(it)
            }
        }

        checkOptimization()

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

        binding.recorderBottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.action_details -> {
                    Log.i("MENU", "DETAILS")
                    binding.mapHolderFrameLayout.visibility = View.GONE
                    binding.detailsLinearLayout.visibility = View.VISIBLE
                    true
                }
                R.id.action_map -> {
                    binding.detailsLinearLayout.visibility = View.GONE
                    binding.mapHolderFrameLayout.visibility = View.VISIBLE
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

        binding.setUpHeartRateSensor.setOnClickListener {

            if (bluetoothAdapter.isEnabled) {
                if (gpsService?.isHeartRateConnected == true) {
                    gpsService?.disconnect()
                } else {
                    gpsService?.startHeartRateScan()
                    binding.setUpHeartRateSensor.text = getString(R.string.connecting)
                    binding.setUpHeartRateSensor.isEnabled = false
                }
            } else {
                //TODO(show normal alert with open action)
                Toast.makeText(this, getString(R.string.enable_bluetooth), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        if (intent.getBooleanExtra(SHOW_UV_ALERT, false)) {
            buildAlertMessageHighUv()
        }

        if (intent.getBooleanExtra(SHOW_WIND_ALERT, false)) {
            buildAlertMessageStrongWind()
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
        binding.mapbox.onStop()
    }

    override fun onStart() {
        super.onStart()
        binding.mapbox.onStart()
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
            showAreYouSureToFinishDialog()
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
        binding.mapbox.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            activityDataReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST_UI_UPDATE)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            activityDataReceiver,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST_HEART_RATE_CONNECTION)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(activityDataReceiver)
        super.onPause()
        binding.mapbox.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapbox.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapbox.onLowMemory()
    }

    //endregion activity lifecycle


    //region map

    @SuppressLint("MissingPermission")//checked
    private fun startLocationTracking() {
        val locationComponent: LocationComponent = mapBox!!.locationComponent
        // Activate with a built LocationComponentActivationOptions object

        // Activate with a built LocationComponentActivationOptions object
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(
                this,
                style!!
            ).build()
        )
        locationComponent.apply {
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING_GPS
            renderMode = RenderMode.GPS
            addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    binding.recenterButton.visibility = View.VISIBLE
                }

                override fun onCameraTrackingChanged(currentMode: Int) {

                }

            })
            zoomWhileTracking(15.0, 2000)
        }
    }

    @SuppressLint("MissingPermission") //checked
    private fun styleLoaded(style: Style) {

        this.style = style

        if (locationPermissionCheck(this)) {
            startLocationTracking()
        }

        if (challenge) {
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            val route =
                Gson().fromJson<ArrayList<MyLocation>>(recordedChallenge!!.routeAsString, typeJson)

            val points = route.map {
                Point.fromLngLat(
                    it.latLng.longitude,
                    it.latLng.latitude,
                    it.altitude
                )
            } as ArrayList<Point>

            val lineString: LineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)
            val geoJsonSource = GeoJsonSource("geojson-source", feature)
            style.addSource(geoJsonSource)
            style.addLayer(
                LineLayer("linelayer", "geojson-source").withProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_SQUARE),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_MITER),
                    PropertyFactory.lineOpacity(1f),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineColor(
                        resources.getColor(
                            R.color.colorAccent,
                            null
                        )
                    )
                )
            )

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

        if (gpsService?.myRoute!!.size < 30) {
            buildAlertMessageNoLocationPoints()
        } else {
            if (gpsService != null) {

                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")

                val currentDate: String = current.format(formatter)
                val gson = Gson()
                val doubleList = gpsService?.myRoute?.map { it.altitude }
                val elevationArray = doubleList!!.toDoubleArray()
                var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
                if (windowSize > Constants.MAX_WINDOW_SIZE)
                    windowSize = Constants.MAX_WINDOW_SIZE
                Log.d("ELEVATION", "the calculated window size is: $windowSize")
                val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
                val filteredElevation = s1.smoothSignal()
                var elevGain = 0.0
                var elevLoss = 0.0
                for (i in 0..filteredElevation.size - 2) {
                    gpsService!!.myRoute[i].altitude = filteredElevation[i]
                    if (filteredElevation[i] < filteredElevation[i + 1]) {
                        elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
                    } else {
                        elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
                    }
                }
                gpsService!!.myRoute[filteredElevation.size - 1].altitude = filteredElevation.last()
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
                    myLocationArrayString,
                    elevGain.roundToInt(),
                    elevLoss.roundToInt()
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
                binding.mapHolderFrameLayout.visibility = View.GONE

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
            voiceCoachCustomDialog.setCanceledOnTouchOutside(true)
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


    private fun showAreYouSureToFinishDialog() {

        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.finish_challenge)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.finish_recording_message)
            findViewById<Button>(R.id.dialogOkButton).text = getString(R.string.yes)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.finish_line
                )
            )
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

        layoutView.findViewById<Button>(R.id.dialogCancelButton).setOnClickListener {
            alertDialog.dismiss()
        }
        layoutView.findViewById<Button>(R.id.dialogOkButton).setOnClickListener {
            finishChallenge()
            alertDialog.dismiss()
        }
    }

    private fun buildAlertMessageHighUv() {

        if (!userManager.uvAlert)
            return

        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.weather_alert)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.high_uv_alert)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.uv_alert
                )
            )
            findViewById<Button>(R.id.dialogCancelButton).visibility = View.GONE
            findViewById<Space>(R.id.dialogButtonSpace).visibility = View.GONE
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

    private fun buildAlertMessageStrongWind() {

        if (!userManager.windAlert)
            return

        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.weather_alert)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.strong_wind_alert)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.wind_red_alert
                )
            )
            findViewById<ImageView>(R.id.dialogImageView).imageTintList = null
            findViewById<Button>(R.id.dialogCancelButton).visibility = View.GONE
            findViewById<Space>(R.id.dialogButtonSpace).visibility = View.GONE
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

    private fun buildAlertMessageNoGps() {

        Log.d("UTILS", "Dialog called")
        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text = getString(R.string.no_gps)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.no_gps_text)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.location_off
                )
            )
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

        layoutView.findViewById<Button>(R.id.dialogCancelButton).setOnClickListener {
            alertDialog.dismiss()
        }
        layoutView.findViewById<Button>(R.id.dialogOkButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            alertDialog.dismiss()
        }
    }

    private fun buildAlertMessageAirplaneIsOn() {


        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.airplane_is_on)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.airplane_mode_text)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.airplane_mode
                )
            )
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
        layoutView.findViewById<Button>(R.id.dialogCancelButton).setOnClickListener {
            alertDialog.dismiss()
        }
        layoutView.findViewById<Button>(R.id.dialogOkButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            alertDialog.dismiss()
        }
    }

    private fun buildAlertMessageNoLocationPoints() {


        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.already_finished)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.this_cannot_be_saved)
            findViewById<Button>(R.id.dialogCancelButton).visibility = View.GONE
            findViewById<Space>(R.id.dialogButtonSpace).visibility = View.GONE
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeRecorderActivity,
                    R.drawable.clock
                )
            )
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
            clean()
            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            alertDialog.dismiss()
            finish()
        }
    }
    //endregion Dialog


    //region helper methods
    @SuppressLint("NewApi", "BatteryLife")
    private fun checkOptimization() {
        val packageName = applicationContext.packageName
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:" + getPackageName())
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (buttonSharedPreferences.getBoolean("started", false)) {
            this.moveTaskToBack(true)
        } else {
            finish()
        }
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
            data.lowercase(Locale.ROOT)
                .contains(getString(string.minute).lowercase(Locale.ROOT)) -> {
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

            if (intent.action == LocationUpdatesService.ACTION_BROADCAST_HEART_RATE_CONNECTION) {
                Log.d("HR", "ACTION")
                if (intent.getBooleanExtra(
                        LocationUpdatesService.HEART_RATE,
                        false
                    )
                ) {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect
                                .createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        vibrator.vibrate(200)
                    }
                    binding.setUpHeartRateSensor.apply {
                        text = getString(R.string.connected)
                        isEnabled = true
                        compoundDrawables[0].setTint(
                            ContextCompat.getColor(
                                this@ChallengeRecorderActivity,
                                R.color.colorGreen
                            )
                        )
                    }
                }
            } else {

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
                val hr = intent.getIntExtra(LocationUpdatesService.HEART_RATE, -1)
                binding.heartRateTextView?.text = if (hr == -1) "-" else hr.toString()

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
                binding.challengeRecorderDistanceTextView.text = "${
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
                    //mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

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
    }

    companion object {
        const val CHALLENGE = "challenge"
        const val RECORDED_CHALLENGE_ID = "recorded"
        const val CREATED_CHALLENGE_INTENT = "createdChallenge"
        var createdChallenge: Boolean = false
            private set
        const val AVG_SPEED = "avgSpeed"
        const val DISTANCE = "distance"
        private val TAG = ChallengeRecorderActivity::class.java.simpleName

        const val SHOW_UV_ALERT = "showUvAlert"
        const val SHOW_WIND_ALERT = "showWindAlert"

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
