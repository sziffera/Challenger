package com.sziffer.challenger


import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.location.Location
import android.os.*
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import java.util.*
import kotlin.collections.ArrayList


class LocationUpdatesService : Service() {

    private val mBinder: IBinder = LocalBinder()
    private var mChangingConfiguration = false
    private var mNotificationManager: NotificationManager? = null
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationCallback: LocationCallback? = null
    private var mServiceHandler: Handler? = null
    private lateinit var builder: NotificationCompat.Builder

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

    /** in ms */
    var durationHelper: Long = 0
        private set

    /** helps calculating the duration */
    private var start: Long = 0

    override fun onCreate() {


        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener {
            if (it == TextToSpeech.SUCCESS) {
                val result: Int = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.v(TAG, "Language is not available.")
                }
            }
        })

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
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Ondestroy")
        setRequestingLocationUpdates(this, false)
        mServiceHandler!!.removeCallbacksAndMessages(null)
        serviceIsRunningInForeground = false
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

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
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        Log.i(TAG, "in onRebind()")
        serviceIsRunningInForeground = false
        stopForeground(true)
        mChangingConfiguration = false
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


    //region location handling


    /**
     * Requesting location updates from the FusedLocationProviderClient.
     * Starts a new thread as well to send broadcast to the RecorderActivity in every sec.
     * In case of a challenge, this thread calls the updateDifference() function
     */
    fun requestLocationUpdates() {


        start = System.currentTimeMillis()
        timerIsRunning = true
        var threadCounter = 0

        object : Thread() {
            override fun run() {
                while (timerIsRunning) {
                    try {

                        //TODO(deprecated)
                        //TODO(refresh and speak based on settings)
                        //always false if auto pause is off
                        if (!zeroSpeed) {

                            if (threadCounter % 10 == 0 && threadCounter > 0) {
                                textToSpeech.speak(
                                    "The distance is" + getStringFromNumber(
                                        1,
                                        distance / 1000
                                    ) + "kilometres", TextToSpeech.QUEUE_FLUSH, null
                                )

                                if (ChallengeRecorderActivity.challenge || ChallengeRecorderActivity.createdChallenge) {
                                    updateDifference()
                                }

                            }
                            threadCounter++
                            updateUI()
                        }

                        sleep(1000)
                    } catch (e: InterruptedException) {
                        Log.i(TAG, "thread interrupted")
                    }
                }
                Log.i(TAG, "thread stopped")
            }
        }.start()


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

        Log.i(TAG, "new location got")
        if (mLocation != null) {
            val tempDistance = location.distanceTo(mLocation)

            //cycling faster than 90km/h is unlikely
            if (tempDistance < 25) {
                if (location.hasSpeed()) {
                    if (maxSpeed < location.speed)
                        maxSpeed = location.speed

                    //if auto pause is set
                    if (ChallengeRecorderActivity.autoPause) {
                        // for auto pause, sets the bool
                        if (location.speed < MINIMUM_SPEED) {
                            Log.i(TAG, "zeroSpeed is: $zeroSpeed")
                            //if the user has just stopped
                            if (!zeroSpeed) {
                                zeroSpeedPauseTime = System.currentTimeMillis()
                            }
                            zeroSpeed = true
                        } else {
                            Log.i(TAG, "zeroSpeed is in else: $zeroSpeed")
                            //the user's speed was zero
                            if (zeroSpeed) {
                                durationHelper -= System.currentTimeMillis() - zeroSpeedPauseTime
                            }
                            zeroSpeed = false
                        }
                    }
                }

                if (!zeroSpeed) {
                    distance += tempDistance
                    currentSpeed = location.speed
                    route.add(LatLng(location.latitude, location.longitude))

                    if (location.hasAltitude())
                        handleNewAltitude(location.altitude)

                    myRoute.add(
                        MyLocation(
                            distance,
                            System.currentTimeMillis() - start + durationHelper,
                            location.speed,
                            correctedAltitude,
                            LatLng(location.latitude, location.longitude)
                        )
                    )
                }

            }
        }
        mLocation = location
    }

    /** stops the handlerThread and the service */
    fun finishAndSaveRoute() {
        stopSelf()
        removeLocationUpdates()
    }

    private fun filterLocation(location: Location?): Boolean {
        //TODO(not implemented)
        return true
    }

    private fun createLocationRequest() {
        mLocationRequest = LocationRequest().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    //endregion location handling

    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    //region helper methods

    /** handles the new altitude and updates the corrected altitude variable. */
    //TODO(works, but too many points -> too high values)
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
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(DISTANCE, distance)
        intent.putExtra(EXTRA_LOCATION, mLocation)
        intent.putExtra(
            DURATION,
            System.currentTimeMillis() - start + durationHelper
        )

        if (ChallengeRecorderActivity.challenge || ChallengeRecorderActivity.createdChallenge)
            intent.putExtra(DIFFERENCE, difference)
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(intent)

        if (serviceIsRunningInForeground) {
            mNotificationManager!!.notify(
                NOTIFICATION_ID,
                updateAndGetNotification()
            )
        }
    }

    //endregion helper methods


    //region notification

    private fun initNotificationBuilder() {
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ChallengeRecorderActivity::class.java), 0
        )
        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .addAction(
                R.drawable.ic_play_circle_outline_24px, getString(R.string.launch_activity),
                activityPendingIntent
            )
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.mipmap.ic_launcher_round
                )
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
            1,
            distance / 1000
        ) + getString(R.string.km) + ", " + getString(
            R.string.duration
        ) + ": " + DateUtils.formatElapsedTime(
            (System.currentTimeMillis() - start + durationHelper) / 1000
        )
    }
    //endregion notification

    companion object {

        private const val PACKAGE_NAME =
            "com.example.challenger"
        private val TAG = LocationUpdatesService::class.java.simpleName

        private const val CHANNEL_ID = "channel_01"
        const val CHALLENGE_BROADCAST = "$PACKAGE_NAME.challengeBroadcast"
        const val ACTION_BROADCAST =
            "$PACKAGE_NAME.broadcast"
        const val DIFFERENCE = "difference"
        const val EXTRA_LOCATION =
            "$PACKAGE_NAME.location"
        const val DISTANCE = "$PACKAGE_NAME.distance"
        const val DURATION = "$PACKAGE_NAME.duration"
        private const val EXTRA_STARTED_FROM_NOTIFICATION =
            PACKAGE_NAME +
                    ".started_from_notification"

        private var serviceIsRunningInForeground: Boolean = false
        private const val ALTITUDES_SIZE = 10

        /** minimum speed in m/s (approximately 2km/h) */
        private const val MINIMUM_SPEED: Double = 0.555555
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2
        const val NOTIFICATION_ID = 12345678
        var previousChallenge: ArrayList<MyLocation> = ArrayList()

    }
}
