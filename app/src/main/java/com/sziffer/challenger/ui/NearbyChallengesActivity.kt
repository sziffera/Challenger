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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.sziffer.challenger.State
import com.sziffer.challenger.adapters.PublicChallengeRecyclerViewAdapter
import com.sziffer.challenger.databinding.ActivityNearbyChallengesBinding
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NearbyChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyChallengesBinding
    private lateinit var viewModel: NearbyChallengesViewModel

    private var recyclerViewAdapter: PublicChallengeRecyclerViewAdapter? = null

    // Coroutine Scope
    private val uiScope = CoroutineScope(Dispatchers.Main)

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
        recyclerViewAdapter = PublicChallengeRecyclerViewAdapter(
            challenges, this,
            GeoLocation(currentLocation!!.latitude, currentLocation!!.longitude)
        )
        binding.recyclerView.apply {
            adapter = recyclerViewAdapter
            layoutManager = LinearLayoutManager(this@NearbyChallengesActivity)
            addItemDecoration(
                DividerItemDecoration(
                    this@NearbyChallengesActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerView.adapter = null
        recyclerViewAdapter = null
    }

    // endregion fetch

    // region location

    @SuppressLint("MissingPermission") //already checked
    private fun checkLastLocation() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val location = it.result
            if (location != null) {
                currentLocation = location
                uiScope.launch {
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
            uiScope.launch { checkLastLocation() }
            Log.d("LOCATION", "permissions granted")
        }
    }


    companion object {
        private const val TAG = "NearbyChallenges"
    }

    // endregion location
}