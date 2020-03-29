package com.example.challenger

import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
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
    private lateinit var saveStartButton: Button
    private var route: ArrayList<LatLng>? = null

    //private lateinit var latLng:  ArrayList<LatLng>
    private var elevationGain: Double = 0.0
    private var elevationLoss: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)
        //TODO(flag to indicate the visibility of save button)

        val intent = intent
        challenge = intent.getParcelableExtra("challenge") as Challenge
        Log.i("CHALLENGE DETAILS",challenge.toString())

        challengeNameEditText = findViewById(R.id.challengeDetailsNameEditText)
        saveStartButton = findViewById(R.id.saveChallengeInDetailsButton)

        if (intent.hasExtra("start")) {
            saveStartButton.text = getString(R.string.start)
            saveStartButton.setOnClickListener {
                startChallenge()
            }
            challengeNameEditText.inputType = InputType.TYPE_NULL
            challengeNameEditText.setText(challenge.n)
        } else {
            saveStartButton.setOnClickListener {
                saveChallenge()
            }
        }

        initVariables()
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    override fun onMapReady(p0: GoogleMap) {
        mMap = p0

        mMap.addPolyline(PolylineOptions().addAll(route))
        val bound = route?.let { zoomToRoute(it) }
        val padding = 50
        val cu = CameraUpdateFactory.newLatLngBounds(bound, padding)
        mMap.animateCamera(cu)
        //TODO(zoom to the route, not to the first point) are
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng[0],5f))
    }

    private fun startChallenge() {
        //TODO(not implemented)
    }

    private fun initVariables() {

        dbHelper = ChallengeDbHelper(this)

        avgSpeedTextView = findViewById(R.id.challengeDetailsAvgSpeedTextView)
        distanceTextView = findViewById(R.id.challengeDetailsDistanceTextView)
        durationTextView = findViewById(R.id.challengeDetailsDurationTextView)

        challengeTypeTextView = findViewById(R.id.challengeDetailsTypeTextView)
        maxSpeedTextView = findViewById(R.id.challengeDetailsMaxSpeedTextView)

        with(challenge){
            val typeJson = object : TypeToken<ArrayList<LatLng>>() {}.type
            route = Gson().fromJson<ArrayList<LatLng>>(stringRoute, typeJson)
            durationTextView.text = DateUtils.formatElapsedTime(dur)
            avgSpeedTextView.text = getStringFromNumber(1, avg) + " km/h"
            distanceTextView.text = getStringFromNumber(3, dst) + " km"
            challengeTypeTextView.text = type
            maxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
        }
/*
        if(route != null) {
            var prevLocation: Location? = null
            latLng = ArrayList()
            for (location in this.route!!) {
                latLng.add(LatLng(location.latitude,location.longitude))
                if(prevLocation != null && location.hasAltitude()) {
                    val tempElevation: Double = location.altitude - prevLocation.altitude
                    if (tempElevation < 0)
                        elevationLoss += tempElevation
                    else
                        elevationGain += tempElevation
                }
                prevLocation = location
            }
        }

        Log.i("DETAILS","gained: $elevationGain, loss: $elevationLoss")

 */
    }

    /**
     * Save challenge to the SQLite database
     */
    private fun saveChallenge() {

        if (challengeNameEditText.text.isEmpty()) {
            challengeNameEditText.error = "Please name the challenge!"
            return
        }
        challenge.n = challengeNameEditText.text.toString()
        dbHelper.addChallenge(challenge)
        Log.i("DETAILS", dbHelper.getAllChallenges().toString())
        Log.i("DETAILS", dbHelper.getItemCount().toString())

    }
}
