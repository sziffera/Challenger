package com.example.challenger

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_create_challenge.*

class CreateChallengeActivity : AppCompatActivity() {

    private var distance: Int = 0
    private var seconds: Int = 0
    private var avgSpeed: Double = 0.0

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_challenge)

        timePicker.setIs24HourView(true)
        distanceNumberPicker.maxValue = 300
        distanceNumberPicker.minValue = 0
        distanceNumberPicker.setOnValueChangedListener { picker, oldVal, newVal ->
            distance = newVal
            avgSpeed = distance.times(3600.0).div(seconds)
            avgSpeedTextView.text = "AVG SPEED: " + getStringFromNumber(1, avgSpeed) + "KM/H"
        }

        timePicker.hour = 0
        timePicker.setOnTimeChangedListener { view, hourOfDay, minute ->
            seconds = hourOfDay.times(3600) + minute.times(60)
            avgSpeed = distance.times(3600.0).div(seconds)
            avgSpeedTextView.text = "AVG SPEED: " + getStringFromNumber(1, avgSpeed) + "KM/H"
        }

        startCreatedChallenge.setOnClickListener {
            //TODO(error handling)
            val startRecordingIntent = Intent(this, ChallengeRecorderActivity::class.java)
            with(startRecordingIntent) {
                putExtra(ChallengeRecorderActivity.CREATED_CHALLENGE_INTENT, true)
                putExtra(ChallengeRecorderActivity.AVG_SPEED, avgSpeed)
                putExtra(ChallengeRecorderActivity.DISTANCE, distance)
            }
            startActivity(startRecordingIntent)
        }
    }
}
