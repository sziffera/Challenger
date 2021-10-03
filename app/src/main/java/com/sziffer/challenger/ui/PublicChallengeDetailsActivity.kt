package com.sziffer.challenger.ui

import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.sziffer.challenger.databinding.ActivityPublicChallengeDetailsBinding
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class PublicChallengeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublicChallengeDetailsBinding
    private lateinit var viewModel: NearbyChallengesViewModel

    // Coroutine Scope
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null

    private var currentLocation: Location? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublicChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)



        viewModel = ViewModelProvider(this, NearbyChallengesViewModelFactory()).get(
            NearbyChallengesViewModel::class.java
        )


    }


}