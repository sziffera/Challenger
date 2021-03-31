package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.ActivityCreateChallengeBinding
import com.sziffer.challenger.utils.getStringFromNumber
import java.util.*

class CreateChallengeActivity : AppCompatActivity() {

    private var distance: Int = 0
    private var seconds: Int = 0
    private var avgSpeed: Double = 0.0

    private lateinit var binding: ActivityCreateChallengeBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
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
        }
        binding.startCreatedChallenge.setOnClickListener {
            if (avgSpeed.toInt() == 0) {
                binding.avgSpeedTextView.startAnimation(
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

    @SuppressLint("SetTextI18n") //just setting numbers
    private fun calculateAndSetAvgSpeed() {
        if (distance != 0 && seconds != 0) {
            avgSpeed = distance.times(3600.0).div(seconds)
            binding.avgSpeedTextView.text =
                "${getString(R.string.avgspeed).toUpperCase(Locale.ROOT)}:" +
                        " ${getStringFromNumber(1, avgSpeed)}KM/H"
        }
    }
}
