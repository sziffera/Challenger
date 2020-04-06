package com.example.challenger

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private var route: ArrayList<MyLocation>? = null
    private var latLngRoute: ArrayList<LatLng> = ArrayList()
    private var update: Boolean = false
    private var isItAChallenge: Boolean = false
    private var elevationGain: Double = 0.0
    private var avgSpeed: Double = 0.0
    private var elevationLoss: Double = 0.0
    private var start: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)
        //TODO(flag to indicate the visibility of save button)

        start = System.currentTimeMillis()

        val intent = intent

        challenge = intent.getParcelableExtra(CHALLENGE_OBJECT) as Challenge
        Log.i("CHALLENGE DETAILS",challenge.toString())

        challengeNameEditText = findViewById(R.id.challengeDetailsNameEditText)
        saveStartButton = findViewById(R.id.saveChallengeInDetailsButton)

        isItAChallenge = intent.getBooleanExtra(IS_IT_A_CHALLENGE, false).also {
            Log.i(TAG, "$IS_IT_A_CHALLENGE is $it")
        }
        update = intent.getBooleanExtra(UPDATE, false).also {
            Log.i(TAG, "$UPDATE is $it")
        }

        //can be null
        previousChallenge = intent.getParcelableExtra(PREVIOUS_CHALLENGE) as Challenge?

        when {
            isItAChallenge -> {
                saveStartButton.text = getString(R.string.challenge_this_activity)
                saveStartButton.setOnClickListener {
                    startChallenge()
                }
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(challenge.n)
            }
            update -> {
                saveStartButton.text = getString(R.string.update_challenge)
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(previousChallenge?.n)
                saveStartButton.setOnClickListener {
                    updateChallenge()
                }
            }
            else -> {
                saveStartButton.setOnClickListener {
                    saveChallenge()
                }
            }
        }

        initVariables()
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }


    override fun onMapReady(p0: GoogleMap) {

        Log.i(TAG, "onMapReady ${System.currentTimeMillis() - start}ms")
        mMap = p0

        val builder = LatLngBounds.builder()
        val fetchData = System.currentTimeMillis()
        if (route != null) {
            for (item in route!!) {
                builder.include(item.latLng)
                latLngRoute.add(item.latLng)
            }
            /*
            for (i in 0..route!!.size - 2) {
                builder.include(route!![i].latLng)
                val speed = (route!![i].speed + route!![i+1].speed).times(1.8)
                if (speed >= avgSpeed) {

                    val line: Polyline = mMap.addPolyline(
                        PolylineOptions()
                            .add(route!![i].latLng, route!![i+1].latLng)
                            .color(Color.GREEN)
                    )


                } else {
                    val line: Polyline = mMap.addPolyline(
                        PolylineOptions()
                            .add(route!![i].latLng, route!![i+1].latLng)
                            .color(Color.RED)
                    )

                }
            }*/
        }
        Log.i(TAG, "fetch data: ${System.currentTimeMillis() - fetchData}ms")

        mMap.setOnMapLoadedCallback {
            Log.i(TAG, "onMapLoadedCallback ${System.currentTimeMillis() - start}ms")
            mMap.addPolyline(PolylineOptions().addAll(latLngRoute))
            //val bound = zoomToRoute(latLngRoute)
            val padding = 50
            val cu = CameraUpdateFactory.newLatLngBounds(builder.build(), padding)
            mMap.animateCamera(cu)
        }


    }

    private fun startChallenge() {

        val intent = Intent(this, ChallengeRecorderActivity::class.java)
        intent.putExtra(ChallengeRecorderActivity.CHALLENGE, true)
        intent.putExtra(ChallengeRecorderActivity.RECORDED_CHALLENGE, challenge)
        dbHelper.close()
        startActivity(intent)
    }

    private fun updateChallenge() {

        if (previousChallenge != null) {
            challenge.n = previousChallenge!!.n
            dbHelper.updateChallenge(previousChallenge!!.id.toInt(), challenge)
            Toast.makeText(this, "Challenge updated successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cant update challenge", Toast.LENGTH_LONG).show()
        }
        startMainActivity()
    }

    private fun saveChallenge() {

        if (challengeNameEditText.text.isEmpty()) {
            challengeNameEditText.error = "Please name the challenge!"
            return
        }
        challenge.n = challengeNameEditText.text.toString()
        dbHelper.addChallenge(challenge)
        Toast.makeText(this, "Challenge saved successfully", Toast.LENGTH_SHORT).show()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        dbHelper.close()
        finish()
    }

    private fun initVariables() {

        dbHelper = ChallengeDbHelper(this)

        avgSpeedTextView = findViewById(R.id.challengeDetailsAvgSpeedTextView)
        distanceTextView = findViewById(R.id.challengeDetailsDistanceTextView)
        durationTextView = findViewById(R.id.challengeDetailsDurationTextView)

        challengeTypeTextView = findViewById(R.id.challengeDetailsTypeTextView)
        maxSpeedTextView = findViewById(R.id.challengeDetailsMaxSpeedTextView)

        with(challenge){
            val time = System.currentTimeMillis()
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            route = Gson().fromJson<ArrayList<MyLocation>>(routeAsString, typeJson)
            Log.i(TAG, "GSON time is ${System.currentTimeMillis() - time}ms")
            durationTextView.text = DateUtils.formatElapsedTime(dur)
            avgSpeedTextView.text = getStringFromNumber(1, avg) + " km/h"
            distanceTextView.text = getStringFromNumber(3, dst) + " km"
            challengeTypeTextView.text = type
            avgSpeed = this.avg
            maxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
        }


    }

    /**
     * Save challenge to the SQLite database
     */

    companion object {
        private val TAG = this::class.java.simpleName
        private const val CHALLENGE_DETAILS = "challengeDetails"
        const val CHALLENGE_OBJECT = "$CHALLENGE_DETAILS.object"
        const val IS_IT_A_CHALLENGE = "$CHALLENGE_DETAILS.isChallenge"
        const val UPDATE = "$CHALLENGE_DETAILS.update"
        const val PREVIOUS_CHALLENGE = "$CHALLENGE_DETAILS.previousChallenge"
    }
}