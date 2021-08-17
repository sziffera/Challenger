package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.FragmentCreateBinding
import com.sziffer.challenger.model.ActivityMainViewModel
import com.sziffer.challenger.utils.getStringFromNumber

class CreateFragment : Fragment() {

    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private var distance: Int = 0
    private var seconds: Int = 0
    private var avgSpeed: Double = 0.0

    private val viewModel: ActivityMainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setAvgSpeed(0.0)
        viewModel.setDistance(0.0)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCreateBinding.inflate(layoutInflater, container, false)


        setFragmentResultListener(KEY_CHALLENGE_START) { _, _ ->
            if (avgSpeed.toInt() == 0) {
                binding.avgSpeedTextView.startAnimation(
                    AnimationUtils.loadAnimation(
                        requireContext(),
                        R.anim.shake
                    )
                )
            }
        }

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





        return binding.root
    }

    @SuppressLint("SetTextI18n") //just setting numbers
    private fun calculateAndSetAvgSpeed() {
        if (distance != 0 && seconds != 0) {
            avgSpeed = distance.times(3600.0).div(seconds)
            viewModel.setAvgSpeed(avgSpeed)
            viewModel.setDistance(distance.toDouble())
            viewModel.setIsTraining(true)
            binding.avgSpeedTextView.text =
                "${getStringFromNumber(1, avgSpeed)}KM/H"
        }
    }

    companion object {
        const val KEY_CHALLENGE_START = "keyChallengeStart"
    }
}