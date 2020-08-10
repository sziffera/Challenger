package com.sziffer.challenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.psambit9791.jdsp.filter.Wiener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.sync.KEY_UPLOAD
import com.sziffer.challenger.sync.updateSharedPrefForSync
import kotlinx.android.synthetic.main.activity_challenge_details.*
import java.io.OutputStreamWriter
import java.util.*
import kotlin.math.abs


class ChallengeDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var avgSpeedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var challengeNameEditText: EditText
    private lateinit var challengeTypeTextView: TextView
    private lateinit var maxSpeedTextView: TextView
    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var challenge: Challenge
    private var previousChallenge: Challenge? = null
    private lateinit var saveStartButton: Button
    private lateinit var builder: LatLngBounds.Builder
    private var route: ArrayList<MyLocation>? = null
    private var elevGain = 0.0
    private var elevLoss = 0.0
    private var update: Boolean = false
    private var isItAChallenge: Boolean = false
    private var avgSpeed: Double = 0.0
    private var start: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)

        dbHelper = ChallengeDbHelper(this)

        // just for calculating performance
        start = System.currentTimeMillis()

        //id for the challenge from the intent
        val id = intent.getLongExtra(CHALLENGE_ID, 0)
        challenge = dbHelper.getChallenge(id.toInt())!!
        builder = LatLngBounds.builder()

        Log.i("CHALLENGE DETAILS", challenge.toString())

        challengeNameEditText = findViewById(R.id.challengeDetailsNameEditText)
        saveStartButton = findViewById(R.id.saveChallengeInDetailsButton)

        isItAChallenge = intent.getBooleanExtra(IS_IT_A_CHALLENGE, false).also {
            Log.i(TAG, "$IS_IT_A_CHALLENGE is $it")
        }
        update = intent.getBooleanExtra(UPDATE, false).also {
            Log.i(TAG, "$UPDATE is $it")
        }

        discardButton.setOnClickListener {
            showDiscardAlertDialog()
        }

        //can be null
        val previousChallengeId = intent.getLongExtra(PREVIOUS_CHALLENGE_ID, -1)
        previousChallenge = dbHelper.getChallenge(previousChallengeId.toInt())

        showChartsButton.setOnClickListener {
            if (route == null) {
                //the Gson() conversion has not finished yet.
                Toast.makeText(this, "Please wait a few seconds", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, ChartsActivity::class.java)
                    .putExtra(ChartsActivity.CHALLENGE_ID, id)
                    .putExtra(ChartsActivity.AVG_SPEED, challenge.avg)
                    .putExtra(ChartsActivity.ELEVATION_GAIN, elevGain)
                    .putExtra(ChartsActivity.ELEVATION_LOSS, elevLoss)
            )
        }

        when {
            //the user chose a Challenge to do it better, and wants to start recording
            isItAChallenge -> {
                discardButton.visibility = View.GONE
                buttonDivSpace.visibility = View.GONE
                saveStartButton.text = getString(R.string.challenge_this_activity)
                saveStartButton.setOnClickListener {
                    if (checkPermissions())
                        startChallenge()
                    else
                        permissionRequest()
                }
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(challenge.name.toUpperCase(Locale.ROOT))

            }
            //the user finished recording a challenged activity, update data with new values
            update -> {
                saveStartButton.text = getString(R.string.update_challenge)
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(previousChallenge?.name?.toUpperCase(Locale.ROOT))

                saveStartButton.setOnClickListener {
                    updateChallenge()
                }
            }
            //this is just a normal recorded challenge
            else -> {
                saveStartButton.setOnClickListener {
                    saveChallenge()
                }
            }
        }
        initVariables()

        //solving the Google Maps touch error caused by ScrollView
        transparentImageView.setOnTouchListener(object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    MotionEvent.ACTION_UP -> {
                        challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    else -> return true
                }
            }
        })

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(p0: GoogleMap) {

        Log.i(TAG, "onMapReady ${System.currentTimeMillis() - start}ms")
        mMap = p0
        runProcessThread()
    }

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
     * In this way, the previous challenge's data is not lost, later I the user
     * can see the improvement. I think this is a better approach, than overriding data.
     */
    private fun updateChallenge() {
        //we have a saved new challenge, and the previous one
        if (previousChallenge != null) {

            challenge.name = previousChallenge!!.name
            dbHelper.updateChallenge(challenge.id.toInt(), challenge)
            updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

            Toast.makeText(
                this, "Challenge saved successfully!"
                , Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Can't update challenge", Toast.LENGTH_LONG).show()
        }
        startMainActivity()
    }

    private fun saveChallenge() {

        if (challengeNameEditText.text.isEmpty()) {
            challengeNameEditText.error = "Please name the challenge!"
            return
        }
        challenge.name = challengeNameEditText.text.toString()

        dbHelper.updateChallenge(challenge.id.toInt(), challenge)

        updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

        Toast.makeText(this, "Challenge saved successfully", Toast.LENGTH_SHORT).show()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        dbHelper.close()
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun initVariables() {

        avgSpeedTextView = findViewById(R.id.challengeDetailsAvgSpeedTextView)
        distanceTextView = findViewById(R.id.challengeDetailsDistanceTextView)
        durationTextView = findViewById(R.id.challengeDetailsDurationTextView)

        maxSpeedTextView = findViewById(R.id.challengeDetailsMaxSpeedTextView)

        with(challenge) {

            durationTextView.text = DateUtils.formatElapsedTime(dur)
            avgSpeedTextView.text = getStringFromNumber(1, avg) + " km/h"
            distanceTextView.text = getStringFromNumber(1, dst) + " km"
            if (type == getString(R.string.running)) {
                challengeTypeImageView.setImageResource(R.drawable.running)
            }
            avgSpeed = this.avg
            maxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
            val avgPace = dur.div(dst)
            avgPaceTextView.text = DateUtils.formatElapsedTime(avgPace.toLong()) + " min/km"
        }
    }

    private fun showDiscardAlertDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle(getString(R.string.discard_challenge))
            .setMessage(getString(R.string.are_you_sure_to_discard))
            .setCancelable(true)
            .setPositiveButton(
                getString(R.string.yes)
            ) { _, _ ->
                discardChallenge()
            }
            .setNegativeButton(
                getString(R.string.no)
            ) { dialog, _ -> dialog.dismiss() }

        val alert: AlertDialog = builder.create()
        alert.show()
    }

    /** removes the temp. stored challenge from DB when the user presses discard button */
    private fun discardChallenge() {
        dbHelper.deleteChallenge(challenge.id).also {
            Log.i(TAG, "delete success is: $it")
        }
        startMainActivity()
    }

    /**
     * Starts a thread that converts the string data to MyLocation array and
     * calculates the LatLngBound for zooming on Map
     * and calculates the elevation data
     **/
    private fun runProcessThread() {
        object : Thread() {
            override fun run() {
                val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
                route = Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
                val polylineOptions = PolylineOptions()
                val elevationArray = DoubleArray(route!!.size)
                for ((index, i) in route!!.withIndex()) {
                    //TODO(calculate elevation)
                    builder.include(i.latLng)
                    elevationArray[index] = i.altitude
                    polylineOptions.add(i.latLng)
                }

                if (elevationArray.size > 100) {
                    val wiener = Wiener(elevationArray, elevationArray.size / 25)
                    val filteredElevation = wiener.wiener_filter()
                    for (i in 10..filteredElevation.size - 10) {
                        if (filteredElevation[i] < filteredElevation[i + 1]) {
                            elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
                        } else {
                            elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
                        }
                    }
                }
                //writeToFile(elevationArray,"unfilteredElevation")
                //writeToFile(filteredElevation,"filteredElevation")

                runOnUiThread {
                    mMap.addPolyline(
                        polylineOptions.color(
                            ContextCompat.getColor(
                                this@ChallengeDetailsActivity,
                                R.color.colorAccent
                            )
                        )
                    )
                    val padding = 50
                    val cu = CameraUpdateFactory.newLatLngBounds(builder.build(), padding)
                    mMap.animateCamera(cu)
                    elevationGainedTextView.text = getStringFromNumber(0, elevGain) + " m"
                    elevationLostTextView.text = getStringFromNumber(0, elevLoss) + " m"
                }

            }
        }.start()
    }


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

    private fun writeToFile(testArray: DoubleArray, name: String) {
        val outputStreamWriter =
            OutputStreamWriter(openFileOutput("$name.txt", Context.MODE_PRIVATE))
        for (item in testArray) {
            outputStreamWriter.write("$item,")
            outputStreamWriter.flush()
        }
        outputStreamWriter.close()
    }

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