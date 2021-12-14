package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.sziffer.challenger.R
import com.sziffer.challenger.State
import com.sziffer.challenger.databinding.ActivityNearbyChallengesBinding
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.ui.custom.NearbyChallengeCategoryHolder
import com.sziffer.challenger.utils.extensions.asGeoLocation
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NearbyChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyChallengesBinding
    private lateinit var viewModel: NearbyChallengesViewModel

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null

    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyChallengesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        viewModel =
            ViewModelProvider(this, NearbyChallengesViewModelFactory()).get(
                NearbyChallengesViewModel::class.java
            )

        checkLastLocation()

    }

    private suspend fun fetchPublicChallenges() {
        currentLocation?.let {
            viewModel.getPublicChallenges(
                GeoLocation(it.latitude, it.longitude),
                applicationContext
            ).collect { state ->
                when (state) {
                    is State.Loading -> loading()
                    is State.Failed -> failed(state.message)
                    is State.Success -> showChallenges(state.data)
                }
            }
        }
    }

    // region fetch

    private fun loading() {
        Log.d(TAG, "Started fetching")
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun failed(message: String) {
        Log.e(TAG, message)
        // todo: show dialog
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.progressBar.visibility = View.GONE
    }

    private fun showChallenges(challenges: ArrayList<PublicChallenge>) {
        Log.d(TAG, "${challenges.count()} challenges fetched: $challenges")
        binding.progressBar.visibility = View.GONE
        createCategories(challenges)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // endregion fetch

    private fun createCategories(challenges: ArrayList<PublicChallenge>) {

        // todo: Later add popular routes too
        val popularRoutes: ArrayList<PublicChallenge> = ArrayList()

        val flatRoutes = challenges.filter {
            (it.elevationGained.toDouble() / it.distance.div(1000.0) <= ROUTE_TYPE_SPLIT
                    && it.elevationLoss.toDouble() / it.distance.div(1000.0) <= ROUTE_TYPE_SPLIT)
        }
        val hillyRoutes = challenges.filter {
            (it.elevationGained.toDouble() / it.distance.div(1000.0) > ROUTE_TYPE_SPLIT
                    && it.elevationLoss.toDouble() / it.distance.div(1000.0) > ROUTE_TYPE_SPLIT)
        }
        val downHillRoutes = challenges.filter { it.elevationLoss > it.elevationGained * 1.1 }
        val upHillRoutes = challenges.filter { it.elevationLoss * 1.1 < it.elevationGained }

        val categories =
            arrayListOf(popularRoutes, flatRoutes, hillyRoutes, downHillRoutes, upHillRoutes)
        val categoryLabels = resources.getStringArray(R.array.challenge_categories)

        for ((index, challengesInCategory) in categories.withIndex()) {
            if (challengesInCategory.isNotEmpty()) {
                binding.nearbyChallengesHolderLinearLayout.addView(
                    NearbyChallengeCategoryHolder(
                        this,
                        challengesInCategory as ArrayList<PublicChallenge>,
                        categoryLabels[index],
                        currentLocation!!.asGeoLocation()
                    )
                )
            }
        }
    }

    // region location

    @SuppressLint("MissingPermission") //already checked
    private fun checkLastLocation() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val location = it.result
            if (location != null) {
                currentLocation = location
                lifecycleScope.launch {
                    fetchPublicChallenges()
                }
            } else {
                requestLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            interval = 1000
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                currentLocation = p0.lastLocation
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest!!, locationCallback!!, Looper.getMainLooper()
        )
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("LOCATION", "permissions granted")
        if (grantResults.isNotEmpty()) {
            lifecycleScope.launch { checkLastLocation() }
            Log.d("LOCATION", "permissions granted")
        }
    }


    companion object {
        private const val TAG = "NearbyChallenges"

        private const val ROUTE_TYPE_SPLIT = 3.8
    }

    // endregion location
}