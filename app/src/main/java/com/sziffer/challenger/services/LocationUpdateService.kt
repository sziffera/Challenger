package com.sziffer.challenger


import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.model.UserManager
import com.sziffer.challenger.ui.ChallengeRecorderActivity
import com.sziffer.challenger.ui.MainActivity
import com.sziffer.challenger.utils.bluetooth.GattHeartRateAttributes
import com.sziffer.challenger.utils.extensions.round
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.utils.requestingLocationUpdates
import com.sziffer.challenger.utils.setRequestingLocationUpdates
import com.welie.blessed.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue


class LocationUpdatesService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val mBinder: IBinder = LocalBinder()
    private var mChangingConfiguration = false
    private var mNotificationManager: NotificationManager? = null
    private lateinit var mLocationRequest: LocationRequest
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mLocationCallback: LocationCallback
    private var mServiceHandler: Handler? = null
    private lateinit var builder: NotificationCompat.Builder
    private lateinit var audioManager: AudioManager
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var userManager: UserManager

    private var heartRate: Int = -1
    private var cadence: Int = -1

    val isHeartRateConnected get() = peripheral != null

    /** helps to restore the TTS in case of focus loss */
    private var played = true

    /** the difference in case of challenge in ms */
    private var difference: Long = 0

    /** helps calculating the time difference */
    private var counter: Int = 0
    private var mLocation: Location? = null

    /** sent to the recorder activity and helps calculating the difference */
    private var currentSpeed: Float = 0f

    /** stores the route points as LatLng objects to draw the route
     * without conversion in the recorder activity
     */
    var route: ArrayList<LatLng> = ArrayList()
        private set

    /** the UI updater thread is running while this is true */
    private var timerIsRunning: Boolean = false

    /** bool for handling auto pause */
    private var zeroSpeed: Boolean = false

    /** measures the elapsed time, while the user's speed was zero */
    private var zeroSpeedPauseTime: Long = 0

    /** to store the last valid duration when auto pause turns on */
    private var lastDuration: Long = 0

    /** stores the route */
    var myRoute: ArrayList<MyLocation> = ArrayList()
        private set

    /** stores the last 4 altitude */
    private var altitudes: ArrayList<Double> = ArrayList(ALTITUDES_SIZE)

    /** the corrected altitude value from the last for altitude */
    private var correctedAltitude = 0.0

    private lateinit var textToSpeech: TextToSpeech

    /** maxSpeed in m/s */
    var maxSpeed: Float = 0f
        private set

    /** in m */
    var distance: Float = 0.0f
        private set

    /** helper variable for voice coach */
    private var distanceForVoiceCoach: Float = 0f

    /** in ms */
    var durationHelper: Long = 0
        private set

    /** helps calculating the duration */
    private var start: Long = 0

    /** helper for voice coach and update difference method */
    private var threadCounter = 0

    /** helper variable for saving data just in every nth new location update */
    private var saveLocationCounter = 0


    //BLUETOOTH
    private var peripheralCallback: BluetoothPeripheralCallback = object :
        BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            super.onServicesDiscovered(peripheral)
            Log.d("BLE", peripheral.services.toString())
        }

        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            processCharacteristicUpdate(value, characteristic)
        }

    }

    private lateinit var bluetoothCentralManagerCallback: BluetoothCentralManagerCallback

    private lateinit var central: BluetoothCentralManager
    private var peripheral: BluetoothPeripheral? = null

    private val filter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString(GattHeartRateAttributes.UUID_HEART_RATE_SERVICE)
    ).build()

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }


    //region service lifecycle
    override fun onCreate() {

        initTextToSpeech()

        initBluetooth()

        userManager = UserManager(this)

        distanceForVoiceCoach = ChallengeRecorderActivity.numberForVoiceCoach.toFloat()

        val sharedPreferences =
            getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)

        Log.i(
            TAG,
            sharedPreferences.getString(MainActivity.FINAL_USER_ID, "")
                .toString() + " is the user id"
        )

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                Log.i(TAG, "location callback new location")
                onNewLocation(locationResult.lastLocation)
            }
        }

        createLocationRequest()
        getLastLocation()

        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)

        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            val mChannel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            )
            mChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            mChannel.setShowBadge(true)
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
        initNotificationBuilder()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")

        val startedFromNotification = intent.getBooleanExtra(
            EXTRA_STARTED_FROM_NOTIFICATION,
            false
        )

        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Ondestroy")
        setRequestingLocationUpdates(this, false)
        mServiceHandler!!.removeCallbacksAndMessages(null)
        serviceIsRunningInForeground = false
        disconnect()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
    //endregion service lifecycle

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    //region service binding
    override fun onBind(intent: Intent): IBinder? {

        Log.i(TAG, "in onBind()")
        serviceIsRunningInForeground = false
        stopForeground(true)
        mChangingConfiguration = false
        if (requestingLocationUpdates(this) && myRoute.size > 1)
            updateUI()
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        Log.i(TAG, "in onRebind()")
        serviceIsRunningInForeground = false
        stopForeground(true)
        mChangingConfiguration = false
        if (requestingLocationUpdates(this) && myRoute.size > 1)
            updateUI()
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        if (!mChangingConfiguration && requestingLocationUpdates(this)) {
            serviceIsRunningInForeground = true
            startForeground(NOTIFICATION_ID, updateAndGetNotification())
        }
        return true
    }
    //endregion


    //region Bluetooth

    private fun initBluetooth() {
        bluetoothCentralManagerCallback =
            object : BluetoothCentralManagerCallback() {
                override fun onDiscoveredPeripheral(
                    peripheral: BluetoothPeripheral,
                    scanResult: ScanResult
                ) {
                    central.stopScan()
                    central.connectPeripheral(peripheral, peripheralCallback)
                }

                override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
                    val characteristic = peripheral.getCharacteristic(
                        UUID.fromString(GattHeartRateAttributes.UUID_HEART_RATE_SERVICE),
                        UUID.fromString(GattHeartRateAttributes.UUID_HEART_RATE_MEASUREMENT)
                    )
                    this@LocationUpdatesService.peripheral = peripheral
                    if (characteristic != null) {
                        peripheral.setNotify(characteristic, true)
                    }
                    sendConnectedBroadcast()
                }

                override fun onDisconnectedPeripheral(
                    peripheral: BluetoothPeripheral,
                    status: HciStatus
                ) {
                    this@LocationUpdatesService.peripheral = null
                }
            }

        try {
            central = BluetoothCentralManager(
                this,
                bluetoothCentralManagerCallback,
                Handler(Looper.myLooper()!!)
            )
        } catch (e: NullPointerException) {
            e.printStackTrace()
            //todo(send not supported broadcast)
        }

    }

    private fun sendConnectedBroadcast() {
        val intent = Intent(ACTION_BROADCAST_HEART_RATE_CONNECTION).apply {
            putExtra(HEART_RATE, true)
        }
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(intent)
    }

    fun startHeartRateScan() {
        central.scanForPeripheralsUsingFilters(mutableListOf(filter))
    }

    private fun processCharacteristicUpdate(
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic
    ) {
        //if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.uuid)) {
        val flag: Int = characteristic.properties
        val format: Int
        if (flag and 0x01 != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16
            Log.d(TAG, "Heart rate format UINT16.")
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8
            Log.d(TAG, "Heart rate format UINT8.")
        }
        val hr: Int = characteristic.getIntValue(format, 1)
        this.heartRate = hr
        Log.d(TAG, "Received heart rate: $heartRate")
        //}
    }

    fun disconnect() {
        try {
            peripheral?.cancelConnection()
            central.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    //endregion Bluetooth

    //region location handling

    /**
     * Requesting location updates from the FusedLocationProviderClient.
     * Starts a new thread as well to send broadcast to the RecorderActivity in every sec.
     * In case of a challenge, this thread calls the updateDifference() function
     */
    fun requestLocationUpdates() {

        start = System.currentTimeMillis()
        timerIsRunning = true
        zeroSpeed = false
        initAndStartUpdaterThread()

        Log.i(TAG, "Requesting location updates")
        setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        try {
            mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.myLooper()
            )
            Log.i(TAG, "location request done")
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, false)
            Log.e(
                TAG,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    /**
     * Removes location updates and sets the bool false which stops the
     * thread started in requestLocationUpdates()
     */

    fun removeLocationUpdates() {

        timerIsRunning = false
        durationHelper += System.currentTimeMillis() - start
        zeroSpeed = true
        if (!ChallengeRecorderActivity.autoPause)
            updateUI()
        Log.i(TAG, "Removing location updates")
        try {
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            setRequestingLocationUpdates(this, false)
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, true)
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }

    private fun getLastLocation() {
        try {
            mFusedLocationClient!!.lastLocation
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        mLocation = task.result
                    } else {
                        Log.w(TAG, "Failed to get location.")
                    }
                }
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }

    /**
     * This method invoked when the location has changed.
     * Stores the new location's LatLng points and the current distance
     * with speed and time.
     * Refreshes the mLocation variable as well
     * Sends notification when the service is running in foreground
     * If auto pause is set:
     *      the timer is paused while the new locations' speed is below
     *      the given MINIMUM_SPEED limit. In this case, the zeroSpeed bool
     *      is set to true, which helps to determine when to start the timer again
     *      If the zeroSpeed is true, but the new location's speed is above the limit
     *      the timer starts again and zeroSpeed is set to false.
     */
    private fun onNewLocation(location: Location) {


        if (mLocation != null) {

            //increasing counter
            saveLocationCounter++

            val tempDistance = location.distanceTo(mLocation).also {
                Log.i(TAG, "temp distance is: $it")
            }

            if (tempDistance <= 0) {
                //this location is unnecessary
                return
            }

            //filtering location jumping
            if (tempDistance < 100) {
                if (location.hasSpeed()) {
                    if (maxSpeed < location.speed && location.speed < 27.7)
                        maxSpeed = location.speed

                    //if auto pause is set
                    if (ChallengeRecorderActivity.autoPause) {
                        // for auto pause, sets the bool
                        if (location.speed < MINIMUM_SPEED) {

                            //if the user has just stopped
                            if (!zeroSpeed) {
                                //saving the current duration to update ui later
                                lastDuration = System.currentTimeMillis() - start + durationHelper
                                Log.d(TAG, "Auto pause is active")
                                if (userManager.startStop)
                                    startSpeaking("Recording paused")
                                zeroSpeedPauseTime = System.currentTimeMillis()
                            }
                            zeroSpeed = true
                        } else {

                            //the user's speed was zero, removing the elapsed time
                            if (zeroSpeed) {
                                if (userManager.startStop)
                                    startSpeaking("Recording resumed")
                                durationHelper -= System.currentTimeMillis() - zeroSpeedPauseTime
                            }
                            zeroSpeed = false
                        }
                    }
                }

                //adding new values if zeroSpeed is false (this is false by default)
                if (!zeroSpeed) {
                    distance += tempDistance
                    currentSpeed = location.speed
                    if (location.hasAltitude())
                        handleNewAltitude(location.altitude)

                    if (saveLocationCounter % LOCATION_SAVE_NUMBER == 0) {
                        route.add(LatLng(location.latitude, location.longitude))

                        myRoute.add(
                            //rounding values to save storage, no need for too many decimals,
                            //the accuracy won't change
                            MyLocation(
                                distance.round(1),
                                heartRate,
                                System.currentTimeMillis() - start + durationHelper,
                                location.speed.round(1),
                                correctedAltitude.round(1),
                                LatLng(location.latitude, location.longitude)
                            )
                        )
                    }
                }

            }

        }
        mLocation = location
    }

    /** stops the service */
    fun finishAndSaveRoute() {
        if (userManager.startStop)
            startSpeaking("Recording finished")
        disconnect() //disconnecting BLE sensor
        stopSelf()
        removeLocationUpdates()
    }


    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    //endregion location handling


    //region voice coach

    private fun initTextToSpeech() {

        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener {
            if (it == TextToSpeech.SUCCESS) {
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == TTS_ID) {
                            played = true
                            Log.i(TAG, "TTS finished")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                audioManager.abandonAudioFocusRequest(audioFocusRequest)
                            } else
                                audioManager.abandonAudioFocus(this@LocationUpdatesService)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId == TTS_ID) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                audioManager.abandonAudioFocusRequest(audioFocusRequest)
                            } else
                                audioManager.abandonAudioFocus(this@LocationUpdatesService)
                        }
                    }

                    override fun onStart(utteranceId: String?) {
                        Log.i(TAG, "TTS started")
                    }

                })
                val result: Int = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.v(TAG, "Language is not available.")
                }
            }
        })


        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()
        }
    }

    /**
     * Initializing text for voice coach. This method converts the duration to hh:mm:ss
     * and determines when to use plural or singular.
     * Requests audio focus as well
     */
    private fun startVoiceCoach() {

        //the user muted the voice coach, no need to continue
        if (ChallengeRecorderActivity.muted) {
            Log.i(TAG, "Muted, so returned")
            return
        }
        try {
            val km: String = if (getStringFromNumber(
                    1, distance
                        .div(1000)
                ).toDouble() == 1.0
            ) {
                "kilometer"
            } else
                "kilometres"

            val milliseconds = System.currentTimeMillis() - start + durationHelper
            val seconds: Int
            var minutes = (milliseconds / (1000 * 60) % 60).toInt()
            val hours = (milliseconds / (1000 * 60 * 60) % 24).toInt()

            val hour: String =
                if (hours == 1)
                    "hour"
                else
                    "hours"
            var min: String =
                if (minutes == 1) {
                    "minute"
                } else
                    "minutes"

            var text: String = ""
            if (userManager.distance)
                Log.i(TAG, "Distance added to speech")
            text += "distance: " +
                    "${
                        getStringFromNumber(
                            1, distance
                                .div(1000)
                        )
                    } $km. "
            if (userManager.duration) {
                Log.i(TAG, "Dur added to speech")
                text += "duration: "
                text += if (hours == 0) {
                    " $minutes $min."
                } else
                    " $hours $hour and $minutes $min."
            }
            if (userManager.avgSpeed) {
                Log.i(TAG, "AVG added to speech")
                val sec = (System.currentTimeMillis() - start + durationHelper).div(1000)
                val avgInMs = distance.div(sec)
                text += "average speed: ${getStringFromNumber(1, avgInMs.times(3.6))} " +
                        "kilometres per hour."
            }


            //adding difference to voice coach in case of challenge
            if ((ChallengeRecorderActivity.challenge ||
                        ChallengeRecorderActivity.createdChallenge) && userManager.difference
            ) {

                updateDifference()

                val diffIsMinus: String =
                    if (difference < 0)
                        "minus"
                    else
                        ""

                val diffTmp = difference.absoluteValue
                seconds = (diffTmp / 1000).toInt() % 60
                minutes = (diffTmp / (1000 * 60) % 60).toInt()

                min =
                    if (minutes % 1 == 0) {
                        "minute"
                    } else
                        "minutes"
                val sec: String =
                    if (seconds == 1)
                        "second"
                    else
                        "seconds"
                text += "The difference is: $diffIsMinus"
                text += if (minutes == 0)
                    " $seconds $sec "
                else
                    " $minutes $min and $seconds $sec "
            }

            startSpeaking(text)

        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {

        when (focusChange) {

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!played) {
                    startVoiceCoach()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {

                if (textToSpeech.isSpeaking) {
                    played = false
                    textToSpeech.stop()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    } else
                        audioManager.abandonAudioFocus(this)
                }

            }
        }
    }

    /** requests audio focus and starts speech when focus gained */
    private fun startSpeaking(text: String) {
        Log.i(TAG, "startSpeaking() invoked")
        //if the voice coach is not enabled, no need to continue
        if (!ChallengeRecorderActivity.isVoiceCoachEnabled)
            return

        val focusRequest: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        val map = HashMap<String, String>()
        map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = TTS_ID

        when (focusRequest) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> return
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, TTS_ID)
            }
        }
    }

    fun sayRecordingStarted() {
        if (userManager.startStop)
            startSpeaking("Recording started")
    }
    //endregion voice coach

    //region helper methods

    /**
     * Starts a thread that updates the ui by sending broadcast and determines
     * when to start Voice Coach based on the user's settings. The thread is started
     * with location update request and running
     * while timerIsRunning is true, which is set to false in case of pause.
     */
    private fun initAndStartUpdaterThread() {
        object : Thread() {

            override fun run() {
                while (timerIsRunning) {
                    try {

                        if (!zeroSpeed) {

                            //start voice coach if it is enabled
                            if (ChallengeRecorderActivity.isVoiceCoachEnabled
                                && threadCounter > 0
                            ) {

                                if (ChallengeRecorderActivity.voiceCoachIsBasedOnDistance) {
                                    //we reached the desired distance for voice coach
                                    if (distanceForVoiceCoach <= distance) {

                                        distanceForVoiceCoach += ChallengeRecorderActivity
                                            .numberForVoiceCoach.toFloat()
                                        startVoiceCoach()
                                    }
                                }

                                if (ChallengeRecorderActivity.voiceCoachIsBasedOnDuration) {
                                    if (threadCounter %
                                        ChallengeRecorderActivity.numberForVoiceCoach == 0
                                    ) {
                                        startVoiceCoach()
                                    }
                                }
                            }
                            //updating difference in case of challenge in every 30 sec
                            if (threadCounter % 10 == 0 && threadCounter > 0) {
                                if (ChallengeRecorderActivity.challenge ||
                                    ChallengeRecorderActivity.createdChallenge
                                ) {
                                    updateDifference()
                                }
                            }
                            threadCounter++
                        }

                        updateUI()

                        sleep(1000)
                    } catch (e: InterruptedException) {
                        Log.i(TAG, "thread interrupted")
                    }
                }
                Log.i(TAG, "thread stopped")
            }
        }.start()
    }


    /**
     * handles the new altitude and updates the corrected altitude variable
     * using moving average. In this way, unreal jumping is corrected.
     */
    private fun handleNewAltitude(altitude: Double) {

        if (altitudes.size == ALTITUDES_SIZE) {
            altitudes.removeAt(0)
            altitudes.add(altitude)
        } else {
            altitudes.add(altitude)
        }
        var sum = 0.0
        for (item in altitudes) {
            sum += item
        }
        correctedAltitude = sum.div(altitudes.size)
    }

    /**
     * updates the difference in milliseconds compared to the previous challenge
     * the difference is calculated based on the challenge type.
     * If it is a recorded challenge:
     *  The method finds the nearest distance to the current distance in the recorded challenge and
     *  and compares the times.
     *  (Maybe the next point is closer, but this does not matter in this case)
     * If it is a created challenge:
     *  The method calculates where the user should be based on the given avg speed, then calculates
     *  the distance difference. Based on the user's speed, the time difference is calculated from
     *  the distance difference.
     */
    private fun updateDifference() {

        if (ChallengeRecorderActivity.createdChallenge) {

            var currentTime = System.currentTimeMillis() - start + durationHelper
            currentTime = currentTime.div(1000)
            val desiredDistance = ChallengeRecorderActivity.avgSpeed.times(currentTime)
            val distanceDifference = (distance - desiredDistance)
            val avg = distance.div(currentTime)
            difference = distanceDifference.div(currentSpeed / 1000).toLong()

        } else {

            while (counter < previousChallenge.size) {
                if (distance <= previousChallenge[counter].distance) {
                    difference =
                        (System.currentTimeMillis() - start + durationHelper) - previousChallenge[counter].time
                    return
                }
                counter++
            }
        }
    }

    /**
     * sends broadcast to the recorder activity and updates the notification when
     * running as a foreground service
     * */
    private fun updateUI() {

        val intent = Intent(ACTION_BROADCAST_UI_UPDATE)

        intent.putExtra(DISTANCE, distance)
        intent.putExtra(EXTRA_LOCATION, mLocation)
        intent.putExtra(AUTO_PAUSE_ACTIVE, zeroSpeed)
        intent.putExtra(HEART_RATE, heartRate)
        if (!zeroSpeed) {
            Log.d(TAG, "Sending normal duration")
            intent.putExtra(
                DURATION,
                System.currentTimeMillis() - start + durationHelper
            )
        } else {
            Log.d(TAG, "Sending last duration")
            intent.putExtra(
                DURATION,
                lastDuration
            )
        }

        intent.putExtra(ALTITUDE, correctedAltitude.toInt())

        if (ChallengeRecorderActivity.challenge || ChallengeRecorderActivity.createdChallenge)
            intent.putExtra(DIFFERENCE, difference)
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(intent)

        if (serviceIsRunningInForeground) {
            if (!zeroSpeed) {
                mNotificationManager!!.notify(
                    NOTIFICATION_ID,
                    updateAndGetNotification()
                )
            }
        }
    }
    //endregion helper methods


    //region notification

    /** initializing the notification and adding channel from Android O  */
    private fun initNotificationBuilder() {
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ChallengeRecorderActivity::class.java), 0
        )
        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .addAction(
                R.drawable.ic_play_circle_outline_24px, getString(R.string.back_to_recording),
                activityPendingIntent
            )
            .setContentText(getNotificationText())
            .setContentTitle(getString(R.string.challenge_recording))
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(activityPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTicker(getNotificationText())
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
            builder.setOngoing(true)
        }
        if (ChallengeRecorderActivity.activityType == "running") {
            builder.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.color_running))
        } else
            builder.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.color_cycling))
    }

    /** creates the notification with current data */
    private fun updateAndGetNotification(): Notification? {
        builder.setContentText(getNotificationText())
            .setOngoing(true)
            .setTicker(getNotificationText())
            .setWhen(System.currentTimeMillis())
        return builder.build()
    }

    /** Notification helper: creates text for the notification with time and duration */
    private fun getNotificationText(): String {
        return getString(R.string.distance) + ": " + getStringFromNumber(
            2,
            distance / 1000
        ) + getString(R.string.km) + ", " + getString(
            R.string.duration
        ) + ": " + DateUtils.formatElapsedTime(
            (System.currentTimeMillis() - start + durationHelper) / 1000
        )
    }
    //endregion notification

    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    companion object {

        private const val PACKAGE_NAME =
            "com.example.challenger"
        private val TAG = LocationUpdatesService::class.java.simpleName

        private const val CHANNEL_ID = "channel_01"
        const val ACTION_BROADCAST_UI_UPDATE =
            "$PACKAGE_NAME.broadcast"
        const val ACTION_BROADCAST_HEART_RATE_CONNECTION = "$PACKAGE_NAME.broadcastHrConnection"
        const val DIFFERENCE = "difference"
        const val EXTRA_LOCATION =
            "$PACKAGE_NAME.location"
        const val HEART_RATE = "$PACKAGE_NAME.heartRate"
        const val DISTANCE = "$PACKAGE_NAME.distance"
        const val DURATION = "$PACKAGE_NAME.duration"
        const val ELEVATION_GAINED = "$PACKAGE_NAME.elevationGained"
        const val ALTITUDE = "$PACKAGE_NAME.altitude"
        const val AUTO_PAUSE_ACTIVE = "$PACKAGE_NAME.autoPauseActive"
        private const val EXTRA_STARTED_FROM_NOTIFICATION =
            PACKAGE_NAME +
                    ".started_from_notification"

        /** helps when to show and remove notification */
        private var serviceIsRunningInForeground: Boolean = false
        private const val ALTITUDES_SIZE = 6

        /** minimum speed in m/s (approximately 2km/h) */
        private const val MINIMUM_SPEED: Double = 0.555555
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2
        const val NOTIFICATION_ID = 12345678
        var previousChallenge: ArrayList<MyLocation> = ArrayList()
        private const val TTS_ID = "VoiceCoach"
        private const val LOCATION_SAVE_NUMBER = 3
    }
}
