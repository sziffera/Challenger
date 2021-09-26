package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.FragmentProfileBinding
import com.sziffer.challenger.model.user.UserManager
import com.sziffer.challenger.ui.user.BodyFatCalculatorActivity
import com.sziffer.challenger.ui.user.LoginActivity
import com.sziffer.challenger.utils.DEFAULT_WEB_CLIENT_ID
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.viewmodels.MainViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!


    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            requireActivity(),
            NearbyChallengesViewModelFactory()
        ).get(MainViewModel::class.java)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentProfileBinding.inflate(layoutInflater, container, false)

        viewModel.calculateStatistics(requireContext())
        viewModel.requestPhotoUri()

        binding.profileImageView.visibility = View.GONE

        UserManager(requireContext()).apply {
            username?.let {
                binding.usernameTextView.text = it
            }
        }

        viewModel.profilePhotoUriLiveData.observe(viewLifecycleOwner, {
            binding.profileImageView.visibility = View.VISIBLE
            Picasso.get().load(it).into(binding.profileImageView)
        })

        viewModel.statisticsLiveData.observe(viewLifecycleOwner, {
            val cyclingStatistics = it[STAT_CYCLING_INDEX]
            val runningStatistics = it[STAT_RUNNING_INDEX]

            with(cyclingStatistics) {

                binding.totalRideTime.text = DateUtils.formatElapsedTime(totalTime.toLong())

                binding.numberOfRides.text = numberOfActivities.toString()
                binding.cyclingAllTimeTextView.text = getStringFromNumber(1, totalDistance) + " km"
                binding.longestRide.text = getStringFromNumber(1, longestDistance) + "km"
                binding.cyclingThisMonthTextView.text = getStringFromNumber(1, thisMonth) + " km"
                binding.cyclingThisWeekTextView.text = getStringFromNumber(1, thisWeek) + " km"
                binding.cyclingThisYearTextView.text = getStringFromNumber(1, thisYear) + " km"
            }


            with(runningStatistics) {
                binding.totalRunningTime.text = DateUtils.formatElapsedTime(totalTime.toLong())

                binding.numberOfRuns.text = numberOfActivities.toString()
                binding.runningAllTimeTextView.text = getStringFromNumber(1, totalDistance) + " km"
                binding.longestRun.text = getStringFromNumber(1, longestDistance) + "km"
                binding.runningThisMonthTextView.text = getStringFromNumber(1, thisMonth) + " km"
                binding.runningThisWeekTextView.text = getStringFromNumber(1, thisWeek) + " km"
                binding.runningThisYearTextView.text = getStringFromNumber(1, thisYear) + " km"
            }

        })

        with(binding) {
            bodyFatCalculatorButton.setOnClickListener {
                startActivity(
                    Intent(requireContext(), BodyFatCalculatorActivity::class.java)
                )
            }
            if (FirebaseManager.isUserValid) {
                signOutButton.setOnClickListener {
                    FirebaseManager.mAuth.signOut()
                    ChallengeDbHelper(requireContext()).apply {
                        deleteDatabase()
                        close()
                    }
                    UserManager(requireContext()).apply {
                        username = null
                        email = null
                        weight = 0
                        height = 0
                    }
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(DEFAULT_WEB_CLIENT_ID)
                        .requestEmail()
                        .build()

                    val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
                    googleSignInClient.signOut()
                    FirebaseAuth.getInstance().signOut()
                    startLoginScreen()
                }
            } else {
                signOutButton.text = getString(R.string.create_an_account)
                signOutButton.setOnClickListener {
                    startLoginScreen()
                }

            }
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startLoginScreen() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
        )
        requireActivity().finish()
    }

    companion object {
        private const val STAT_CYCLING_INDEX = 0
        private const val STAT_RUNNING_INDEX = 1
    }
}