package com.sziffer.challenger

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_create_challenge.*
import java.util.*

class CreateChallengeActivity : AppCompatActivity() {

    private var distance: Int = 0
    private var seconds: Int = 0
    private var avgSpeed: Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_challenge)

        timePicker.setIs24HourView(true)
        distanceNumberPicker.maxValue = 300
        distanceNumberPicker.minValue = 0
        distanceNumberPicker.setOnValueChangedListener { _, _, newVal ->
            distance = newVal
            calculateAndSetAvgSpeed()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour = 0
            timePicker.minute = 0
        }
        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            seconds = hourOfDay.times(3600) + minute.times(60)
            calculateAndSetAvgSpeed()
        }

        startCreatedChallenge.setOnClickListener {
            if (avgSpeed.toInt() == 0) {
                avgSpeedTextView.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.shake
                    )
                )
                return@setOnClickListener
            }
            val startRecordingIntent = Intent(this, ChallengeRecorderActivity::class.java)
            with(startRecordingIntent) {
                putExtra(ChallengeRecorderActivity.CREATED_CHALLENGE_INTENT, true)
                putExtra(ChallengeRecorderActivity.AVG_SPEED, avgSpeed)
                putExtra(ChallengeRecorderActivity.DISTANCE, distance)
            }
            startActivity(startRecordingIntent)
        }
    }

    private fun calculateAndSetAvgSpeed() {
        if (distance != 0 && seconds != 0) {
            avgSpeed = distance.times(3600.0).div(seconds)
            avgSpeedTextView.text = "${getString(R.string.avgspeed).toUpperCase(Locale.ROOT)}:" +
                    " ${getStringFromNumber(1, avgSpeed)}KM/H"
        }
    }
}
