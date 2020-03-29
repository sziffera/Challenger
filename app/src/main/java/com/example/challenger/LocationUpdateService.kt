package com.example.challenger


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
    private var mLocation: Location? = null
    private var timerIsRunning: Boolean = false
    private lateinit var textToSpeech: TextToSpeech
    var maxSpeed: Float = 0f
        private set
    var distance: Float = 0.0f
        private set
    var duration: Long = 0
        private set
    private var start: Long = 0
    var route: ArrayList<LatLng>? = ArrayList()
        private set


    //TODO(create and store details for challenge)

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
                onNewLocation(locationResult.lastLocation)
            }
        }
        route = ArrayList()
        createLocationRequest()
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            val mChannel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    //region service binding
    override fun onBind(intent: Intent): IBinder? {

        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        if (!mChangingConfiguration && requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, LocationUpdatesService::class.java))
            } else {
                startForeground(NOTIFICATION_ID, getNotification())
            }
        }
        return true
    }
    //endregion

    override fun onDestroy() {
        mServiceHandler!!.removeCallbacksAndMessages(null)
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    //region location handling
    fun requestLocationUpdates() {

        start = System.currentTimeMillis()
        timerIsRunning = true
        var counter = 0
        object : Thread() {
            override fun run() {
                while (timerIsRunning) {
                    try {
                        if (counter % 60 == 0 && counter > 0) {
                            textToSpeech.speak(
                                "The distance is" + getStringFromNumber(
                                    1,
                                    distance / 1000
                                ) + "km", TextToSpeech.QUEUE_FLUSH, null
                            )
                        }
                        counter++
                        val intent = Intent(ACTION_BROADCAST)
                        intent.putExtra(DISTANCE, distance)
                        intent.putExtra(EXTRA_LOCATION, mLocation)
                        intent.putExtra(DURATION, System.currentTimeMillis() - start + duration)
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
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
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, false)
            Log.e(
                TAG,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    fun removeLocationUpdates() {

        timerIsRunning = false
        duration += System.currentTimeMillis() - start
        Log.i(TAG, "Removing location updates")
        try {
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, true)
            Log.e(
                TAG,
                "Lost location permission. Could not remove updates. $unlikely"
            )
        }
    }


    private fun onNewLocation(location: Location) {

        Log.i(TAG, "New location: $location")
        if (mLocation != null) {
            val tempDistance = location.distanceTo(mLocation)

            //cycling faster than 90km/h is unlikely
            if (tempDistance < 25) {
                if (location.hasSpeed()) {
                    if (maxSpeed < location.speed)
                        maxSpeed = location.speed
                }
                distance += tempDistance
                route?.add(LatLng(location.latitude, location.longitude))
            }
        }
        mLocation = location
/*
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(DISTANCE, distance)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
*/
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager!!.notify(
                NOTIFICATION_ID,
                getNotification()
            )
        }
    }

    fun finishAndSaveRoute() {
        removeLocationUpdates()

    }

    private fun filterLocation(location: Location?): Boolean {
        //TODO(not implemented)
        return true
    }

    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

    }
    //endregion

    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    private fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as ActivityManager
        //there is no replacement yet
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    private fun getNotification(): Notification? {


        val activityPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ChallengeRecorderActivity::class.java), 0
        )

        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
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
                .setTicker(getNotificationText())
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }
        return builder.build()
    }

    private fun getNotificationText(): String {
        return getString(R.string.distance) + ": " + getStringFromNumber(
            1,
            distance / 1000
        ) + getString(R.string.km) + ", " + getString(
            R.string.duration
        ) + ": " + DateUtils.formatElapsedTime(
            (System.currentTimeMillis() - start + duration) / 1000
        )

    }

    companion object {

        private const val PACKAGE_NAME =
            "com.example.challenger"
        private val TAG = LocationUpdatesService::class.java.simpleName

        private const val CHANNEL_ID = "channel_01"
        const val ACTION_BROADCAST =
            "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION =
            "$PACKAGE_NAME.location"
        const val DISTANCE = "$PACKAGE_NAME.distance"
        const val DURATION = "$PACKAGE_NAME.duration"
        private const val EXTRA_STARTED_FROM_NOTIFICATION =
            PACKAGE_NAME +
                    ".started_from_notification"

        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2
        private const val NOTIFICATION_ID = 12345678
    }
}
