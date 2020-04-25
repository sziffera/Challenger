package com.sziffer.challenger

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.View
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
import com.sziffer.challenger.sync.KEY_UPLOAD
import com.sziffer.challenger.sync.updateSharedPrefForSync
import kotlinx.android.synthetic.main.activity_challenge_details.*
import kotlin.math.absoluteValue


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

        start = System.currentTimeMillis()

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

        discardButton.setOnClickListener {
            showDiscardAlertDialog()
            discardChallenge()
            startMainActivity()
        }

        //can be null
        previousChallenge = intent.getParcelableExtra(PREVIOUS_CHALLENGE) as Challenge?

        showChartsButton.setOnClickListener {
            if (route == null) {
                Toast.makeText(this, "Please wait a few seconds", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, ChartsActivity::class.java)
                    .putParcelableArrayListExtra(ChartsActivity.CHALLENGE_DATA_ARRAY, route)
                    .putExtra(ChartsActivity.AVG_SPEED, challenge.avg)
            )
        }


        when {
            //the user chose a Challenge to do it better, and wants to start recording
            isItAChallenge -> {
                discardButton.visibility = View.GONE
                saveStartButton.text = getString(R.string.challenge_this_activity)
                saveStartButton.setOnClickListener {
                    startChallenge()
                }
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(challenge.name.toUpperCase())

            }
            //the user finished recording a challenged activity, update data with new values
            update -> {
                saveStartButton.text = getString(R.string.update_challenge)
                challengeNameEditText.inputType = InputType.TYPE_NULL
                challengeNameEditText.setText(previousChallenge?.name?.toUpperCase())

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
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(p0: GoogleMap) {

        Log.i(TAG, "onMapReady ${System.currentTimeMillis() - start}ms")
        mMap = p0
        //TODO(cleaning and optimizing the code)
        val builder = LatLngBounds.builder()
        val fetchData = System.currentTimeMillis()
        var all = 0.0
        var temp = 1
        val alts: ArrayList<Double> = ArrayList()
        var tmpDst = 0f
        Log.i(TAG, "the last distance is: ${route!![route!!.size - 1].distance}km")
        if (route != null) {
            for (item in route!!) {

                tmpDst += item.distance
                if (tmpDst >= 100f) {
                    alts.add(item.altitude)
                    tmpDst = 0f
                }


                builder.include(item.latLng)
                latLngRoute.add(item.latLng)
            }

            for (item in alts) {
                if (temp < alts.size) {
                    all += item
                    val tempElevation = alts[temp]
                    val diff = tempElevation - item
                    Log.i(TAG, "the altitude difference is: $diff")
                    if (diff < 0) {
                        elevationLoss += diff.absoluteValue
                    } else {
                        elevationGain += diff
                    }
                    temp++
                }
            }

            Log.i(TAG, "the altitude list is $alts")
            Log.i(TAG, "all is $all")

            elevationGainedTextView.text = getStringFromNumber(0, elevationGain) + " m"
            elevationLostTextView.text = getStringFromNumber(0, elevationLoss) + " m"

            Log.i(TAG, "Elevation data. Gained: $elevationGain, loss: $elevationLoss")
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
        ChallengeManager.isChallenge = true
        ChallengeManager.previousChallenge = challenge
        ChallengeManager.isUpdate = true
        dbHelper.close()
        startActivity(intent)
    }

    private fun updateChallenge() {

        //we have a saved new challenge, and the previous one
        if (previousChallenge != null) {

            challenge.name = previousChallenge!!.name
            challenge.firebaseId = previousChallenge!!.firebaseId
            dbHelper.updateChallenge(previousChallenge!!.id.toInt(), challenge)
            updateSharedPrefForSync(applicationContext, previousChallenge!!.firebaseId, KEY_UPLOAD)
            dbHelper.deleteChallenge(challenge.id).also {
                Log.i(TAG, "the delete bool in update is: $it")
            }

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
        challenge.name = challengeNameEditText.text.toString()

        dbHelper.updateChallenge(challenge.id.toInt(), challenge)
        /*
        val id = dbHelper.addChallenge(challenge).also {
            Log.i(TAG, "the id of the inserted item is: $it")
        }
         */

        updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

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
            distanceTextView.text = getStringFromNumber(1, dst) + " km"
            challengeTypeTextView.text = type
            avgSpeed = this.avg
            maxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
            val avgPace = dur.div(dst)
            avgPaceTextView.text = DateUtils.formatElapsedTime(avgPace.toLong()) + " min/km"
        }
    }

    private fun showDiscardAlertDialog() {
        //TODO(not implemented)
    }

    private fun discardChallenge() {
        val ok = dbHelper.deleteChallenge(challenge.id).also {
            Log.i(TAG, "delete success is: $it")
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName
        private const val CHALLENGE_DETAILS = "challengeDetails"
        const val CHALLENGE_OBJECT = "$CHALLENGE_DETAILS.object"
        const val IS_IT_A_CHALLENGE = "$CHALLENGE_DETAILS.isChallenge"
        const val UPDATE = "$CHALLENGE_DETAILS.update"
        const val PREVIOUS_CHALLENGE = "$CHALLENGE_DETAILS.previousChallenge"
    }
}