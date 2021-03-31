package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.FragmentCreateBinding
import com.sziffer.challenger.utils.getStringFromNumber
import java.util.*

class CreateFragment : Fragment() {

    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private var distance: Int = 0
    private var seconds: Int = 0
    private var avgSpeed: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCreateBinding.inflate(layoutInflater, container, false)

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
                        requireContext(),
                        R.anim.shake
                    )
                )
                return@setOnClickListener
            }
            val startRecordingIntent =
                Intent(requireContext(), ChallengeRecorderActivity::class.java)
            with(startRecordingIntent) {
                putExtra(ChallengeRecorderActivity.CREATED_CHALLENGE_INTENT, true)
                putExtra(ChallengeRecorderActivity.AVG_SPEED, avgSpeed)
                putExtra(ChallengeRecorderActivity.DISTANCE, distance)
            }
            startActivity(startRecordingIntent)
        }


        return binding.root
    }

    @SuppressLint("SetTextI18n") //just setting numbers
    private fun calculateAndSetAvgSpeed() {
        if (distance != 0 && seconds != 0) {
            avgSpeed = distance.times(3600.0).div(seconds)
            binding.avgSpeedTextView.text =
                "${getStringFromNumber(1, avgSpeed)}KM/H"
        }
    }
}