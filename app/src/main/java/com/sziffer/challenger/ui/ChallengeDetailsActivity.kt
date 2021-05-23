package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChallengeDetailsBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.model.HeartRateZones
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.sync.KEY_UPLOAD
import com.sziffer.challenger.sync.updateSharedPrefForSync
import com.sziffer.challenger.utils.*
import java.io.*
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs


class ChallengeDetailsActivity : AppCompatActivity() {

    //private lateinit var mMap: GoogleMap

    private var mapBox: MapboxMap? = null
    private var snapshotter: MapSnapshotter? = null

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var challenge: Challenge
    private var previousChallenge: Challenge? = null

    private var route: ArrayList<MyLocation>? = null
    private var elevGain = 0.0
    private var elevLoss = 0.0
    private var sharingImageBitmap: Bitmap? = null
    private var update: Boolean = false
    private var isItAChallenge: Boolean = false
    private var avgSpeed: Double = 0.0
    private var maxHr = -1
    private var avgHr = -1
    private var start: Long = 0

    private var heartRateZones: HeartRateZones? = null

    private lateinit var binding: ActivityChallengeDetailsBinding

    //region lifecycle
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.challengeDetailsMap.onCreate(savedInstanceState)
        binding.challengeDetailsMap.getMapAsync { mapboxMap ->
            this.mapBox = mapboxMap
            mapBox!!.setStyle(Style.OUTDOORS) {
                runProcessThread(it)
            }
        }

        setSupportActionBar(binding.challengeDetailsToolbar)

        dbHelper = ChallengeDbHelper(this)

        // just for calculating performance
        start = System.currentTimeMillis()

        //id for the challenge from the intent
        val id = intent.getLongExtra(CHALLENGE_ID, 0)
        challenge = dbHelper.getChallenge(id.toInt())!!

        Log.i("CHALLENGE DETAILS", challenge.toString())


        isItAChallenge = intent.getBooleanExtra(IS_IT_A_CHALLENGE, false).also {
            Log.i(TAG, "$IS_IT_A_CHALLENGE is $it")
        }
        update = intent.getBooleanExtra(UPDATE, false).also {
            Log.i(TAG, "$UPDATE is $it")
        }

        binding.discardButton.setOnClickListener {
            showDiscardAlertDialog()
        }

        //can be null
        val previousChallengeId = intent.getLongExtra(PREVIOUS_CHALLENGE_ID, -1)
        previousChallenge = dbHelper.getChallenge(previousChallengeId.toInt())

        binding.showChartsButton.setOnClickListener {
            if (route == null) {
                //the Gson() conversion has not finished yet - not too possible to happen.
                Toast.makeText(
                    this, getString(R.string.please_wait),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, ChartsActivity::class.java)
                    .putExtra(ChartsActivity.CHALLENGE_ID, id)
                    .putExtra(ChartsActivity.AVG_SPEED, challenge.avg)
                    .putExtra(ChartsActivity.ELEVATION_GAIN, elevGain)
                    .putExtra(ChartsActivity.ELEVATION_LOSS, elevLoss)
                    .putExtra(ChartsActivity.SHOW_HR, maxHr > 0)
                    .putExtra(ChartsActivity.HEART_RATE_ZONES, heartRateZones)
                    .putExtra(ChartsActivity.MAX_HR, maxHr)
                    .putExtra(ChartsActivity.AVG_HR, avgHr)
            )
        }

        binding.writeToFileButton.setOnClickListener {
            //route?.let { it1 -> writeToFile(it1,"saab") }

            //            val challengeCopy = challenge
            //            val currentDate: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //                val current = LocalDateTime.now()
            //                val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
            //                current.format(formatter)
            //
            //            } else {
            //                val date = Date()
            //                val formatter = SimpleDateFormat("dd-MM-yyyy. HH:mm")
            //                formatter.format(date)
            //            }
            //            challengeCopy.date = currentDate
            //            challengeCopy.firebaseId = UUID.randomUUID().toString()
            //
            //            updateSharedPrefForSync(applicationContext, challengeCopy.firebaseId, KEY_UPLOAD)
            //
            //            dbHelper.addChallenge(challengeCopy)


        } //just for testing and car analysis for my brother

        when {
            //the user chose a Challenge to do it better, and wants to start recording
            isItAChallenge -> {
                binding.discardButton.visibility = View.GONE
                binding.buttonDivSpace.visibility = View.GONE
                binding.saveChallengeInDetailsButton.text =
                    getString(R.string.challenge_this_activity)
                binding.saveChallengeInDetailsButton.setOnClickListener {
                    if (locationPermissionCheck(this))
                        startChallenge()
                    else
                        locationPermissionRequest(this, this)
                }
                binding.challengeDetailsNameEditText.visibility = View.GONE
                supportActionBar?.title = challenge.name.capitalize(Locale.ROOT)

            }
            //the user finished recording a challenged activity, update data with new values
            update -> {
                binding.saveChallengeInDetailsButton.text = getString(R.string.update_challenge)
                binding.challengeDetailsNameEditText.visibility = View.GONE
                supportActionBar?.title = previousChallenge?.name?.capitalize(Locale.ROOT)

                binding.saveChallengeInDetailsButton.setOnClickListener {
                    updateChallenge()
                }
            }
            //this is just a normal recorded challenge
            else -> {
                binding.saveChallengeInDetailsButton.setOnClickListener {
                    saveChallenge()
                }
            }
        }
        initVariables()

        //solving the Google Maps touch error caused by ScrollView
        binding.transparentImageView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    MotionEvent.ACTION_UP -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    else -> return true
                }
            }
        })


        binding.shareImageButton.setOnClickListener {

            startActivity(
                Intent(
                    this, ShareActivity::class.java
                ).apply {
                    putExtra(CHALLENGE_ID, challenge.id)
                }
            )
        }

    }

    override fun onStart() {
        super.onStart()
        binding.challengeDetailsMap.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.challengeDetailsMap.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.challengeDetailsMap.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.challengeDetailsMap.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.challengeDetailsMap.onLowMemory()
    }


    //endregion lifecycle

    //region helper methods

    private fun startChallenge() {
        val intent = Intent(this, ChallengeRecorderActivity::class.java)
        intent.putExtra(ChallengeRecorderActivity.CHALLENGE, true)
        intent.putExtra(ChallengeRecorderActivity.RECORDED_CHALLENGE_ID, challenge.id.toInt())
        dbHelper.close()
        startActivity(intent)
    }

    /**
     * updating the previous challenge
     * this is just a normal saving, but with the previous challenge's name.
     * In this way, the previous challenge's data is not lost, later the user
     * can see the improvement. I think this is a better approach, than overriding data.
     */
    private fun updateChallenge() {
        //we have a saved new challenge, and the previous one
        if (previousChallenge != null) {

            challenge.name = previousChallenge!!.name
            dbHelper.updateChallenge(challenge.id.toInt(), challenge)
            updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

            Toast.makeText(
                this, getString(R.string.save_ok), Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.cant_save), Toast.LENGTH_LONG).show()
        }
        startMainActivity()
    }

    private fun saveChallenge() {

        if (binding.challengeDetailsNameEditText.text.isEmpty()) {
            challenge.name = challenge.type
        } else
            challenge.name = binding.challengeDetailsNameEditText.text.toString()

        dbHelper.updateChallenge(challenge.id.toInt(), challenge)

        updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

        Toast.makeText(this, getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        dbHelper.close()
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun initVariables() {


        with(challenge) {

            binding.challengeDetailsDurationTextView.text = DateUtils.formatElapsedTime(dur)
            binding.challengeDetailsAvgSpeedTextView.text = getStringFromNumber(1, avg) + " km/h"
            binding.challengeDetailsDistanceTextView.text = getStringFromNumber(1, dst) + " km"

            avgSpeed = this.avg
            binding.challengeDetailsMaxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
            val avgPace = dur.div(dst)
            binding.avgPaceTextView.text = DateUtils.formatElapsedTime(avgPace.toLong()) + " min/km"
        }
    }

    private fun showDiscardAlertDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.discard_challenge)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.are_you_sure_to_discard)
            findViewById<Button>(R.id.dialogOkButton).text = getString(R.string.yes)
            findViewById<Button>(R.id.dialogCancelButton).text = getString(R.string.no)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeDetailsActivity,
                    R.drawable.delete
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
            discardChallenge()
            alertDialog.dismiss()
        }
    }

    /** removes the temp. stored challenge from DB when the user presses discard button */
    private fun discardChallenge() {
        dbHelper.deleteChallenge(challenge.id).also {
            Log.i(TAG, "delete success is: $it")
        }
        startMainActivity()
    }

    //endregion helper methods

    //region processing
    /**
     * Starts a thread that converts the string data to MyLocation array and
     * calculates the LatLngBound for zooming on Map
     * alos filter calculates the elevation data
     * - the filtering will be remooved as soon as the method is final, will be moved to the saving part
     **/
    private fun runProcessThread(style: Style) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {

            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            route = Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
            val elevationArray = DoubleArray(route!!.size)
            val points = ArrayList<Point>()
            val latLngBoundsBuilder = LatLngBounds.Builder()


            var hrSum = 0
            var hr = false
            if (route?.get(0)?.hr == -1) {
                for ((index, i) in route!!.withIndex()) {
                    elevationArray[index] = i.altitude
                    points.add(
                        Point.fromLngLat(
                            i.latLng.longitude, i.latLng.latitude, i.altitude
                        )
                    )
                    latLngBoundsBuilder.include(
                        LatLng(
                            i.latLng.latitude,
                            i.latLng.longitude,
                            i.altitude
                        )
                    )
                }
            } else {
                heartRateZones = HeartRateZones()
                hr = true
                for ((index, myLocation) in route!!.withIndex()) {

                    when (myLocation.hr) {
                        in 0..97 -> heartRateZones!!.relaxed++
                        in 98..116 -> heartRateZones!!.moderate++
                        in 117..136 -> heartRateZones!!.weightControl++
                        in 137..155 -> heartRateZones!!.aerobic++
                        in 156..175 -> heartRateZones!!.anaerobic++
                        else -> heartRateZones!!.vo2Max++
                    }

                    latLngBoundsBuilder.include(
                        LatLng(
                            myLocation.latLng.latitude,
                            myLocation.latLng.longitude,
                            myLocation.altitude
                        )
                    )
                    points.add(
                        Point.fromLngLat(
                            myLocation.latLng.longitude,
                            myLocation.latLng.latitude,
                            myLocation.altitude
                        )
                    )
                    elevationArray[index] = myLocation.altitude
                }

                heartRateZones!!.apply {
                    relaxed /= route!!.size.toDouble()
                    moderate /= route!!.size.toDouble()
                    weightControl /= route!!.size.toDouble()
                    aerobic /= route!!.size.toDouble()
                    anaerobic /= route!!.size.toDouble()
                    vo2Max /= route!!.size.toDouble()
                }

                hrSum = route!!.sumOf { it.hr }
                maxHr = route!!.maxOf { it.hr }

                Log.d("HEART_RATE", heartRateZones.toString())

                avgHr = hrSum.div(route!!.size)
            }

            if (elevationArray.size > Constants.MIN_ROUTE_SIZE) {


                var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
                if (windowSize > Constants.MAX_WINDOW_SIZE)
                    windowSize = Constants.MAX_WINDOW_SIZE
                Log.d("ELEVATION", "the calculated window size is: $windowSize")
                val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
                val filteredElevation = s1.smoothSignal()

                for (i in 0..filteredElevation.size - 2) {
                    if (filteredElevation[i] < filteredElevation[i + 1]) {
                        elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
                    } else {
                        elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
                    }
                }
            }


            handler.post {
                val lineString: LineString = LineString.fromLngLats(points)
                val feature = Feature.fromGeometry(lineString)
                val geoJsonSource = GeoJsonSource("geojson-source", feature)
                val lineLayer = LineLayer("linelayer", "geojson-source").withProperties(
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


                style.addSource(geoJsonSource)
                style.addLayer(lineLayer)

                mapBox?.animateCamera(
                    com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(
                        latLngBoundsBuilder.build(),
                        100
                    ), 2000
                )


                binding.elevationGainedTextView.text = getStringFromNumber(0, elevGain) + " m"
                binding.elevationLostTextView.text = getStringFromNumber(0, elevLoss) + " m"
                if (hr) {
                    binding.apply {
                        maxHeartRateTextView.text = "$maxHr bpm"
                        avgHeartRateTextView.text = "$avgHr bpm"
                    }
                } else {
                    binding.apply {
                        maxHeartRateTextView.text = "--"
                        avgHeartRateTextView.text = "--"
                    }
                }
            }
        }
    }

    //endregion processing



    //region for testing
    private fun writeToFile(testArray: DoubleArray) {
        val outputStreamWriter =
            OutputStreamWriter(openFileOutput("altitude_original.txt", Context.MODE_PRIVATE))



        outputStreamWriter.write("distance (in metres),altitude (in metres)\n")

        //Toast.makeText(this, testArray.size.toString() + " items", Toast.LENGTH_LONG).show()
        for ((index, altitude) in testArray.withIndex()) {
            outputStreamWriter.write("${route!![index].distance};${route!![index].altitude}\n")
        }
        outputStreamWriter.flush()
        outputStreamWriter.close()

    }


    private fun contentValues(): ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    //endregion for testing

    companion object {
        private val TAG = this::class.java.simpleName
        private const val REQUEST = 112
        private const val CHALLENGE_DETAILS = "challengeDetails"
        const val CHALLENGE_ID = "$CHALLENGE_DETAILS.id"
        const val IS_IT_A_CHALLENGE = "$CHALLENGE_DETAILS.isChallenge"
        const val UPDATE = "$CHALLENGE_DETAILS.update"
        const val PREVIOUS_CHALLENGE_ID = "$CHALLENGE_DETAILS.previousChallengeId"
    }
}