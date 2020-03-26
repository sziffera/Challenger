package com.example.challenger

import android.location.Location
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var saveButton: Button
    private var route: ArrayList<Location>? = null
    private lateinit var latLng:  ArrayList<LatLng>
    private var elevationGain: Double = 0.0
    private var elevationLoss: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)
        //TODO(flag to indicate the visibility of save button)

        val intent = intent
        challenge = intent.getParcelableExtra("challenge") as Challenge
        Log.i("CHALLENGE DETAILS",challenge.toString())

        initVariables()
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)



    }

    override fun onMapReady(p0: GoogleMap) {
        mMap = p0

        mMap.addPolyline(PolylineOptions().addAll(latLng))
        //TODO(zoom to the route, not to the first point) are
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng[0],5f))
    }

    private fun initVariables() {

        dbHelper = ChallengeDbHelper(this)
        saveButton = findViewById(R.id.saveChallengeInDetailsButton)
        saveButton.setOnClickListener {
            saveChallenge()
        }

        avgSpeedTextView = findViewById(R.id.challengeDetailsAvgSpeedTextView)
        distanceTextView = findViewById(R.id.challengeDetailsDistanceTextView)
        durationTextView = findViewById(R.id.challengeDetailsDurationTextView)
        challengeNameEditText = findViewById(R.id.challengeDetailsNameEditText)
        challengeTypeTextView = findViewById(R.id.challengeDetailsTypeTextView)
        maxSpeedTextView = findViewById(R.id.challengeDetailsMaxSpeedTextView)

        with(challenge){
            durationTextView.text = DateUtils.formatElapsedTime(dur)
            avgSpeedTextView.text = "%.2f".format(avg) + " km/h"
            distanceTextView.text = "%.2f".format(dst) + " km"
            challengeTypeTextView.text = type
            maxSpeedTextView.text = "%.2f".format(mS) + " km/h"
            val type =  object : TypeToken<ArrayList<Location>>() {}.type
            route = Gson().fromJson<ArrayList<Location>>(stringRoute, type)
        }

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
