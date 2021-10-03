package com.sziffer.challenger.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.database.PublicChallengesRepository

class NearbyChallengesViewModel(private val repository: PublicChallengesRepository) : ViewModel() {

    fun getPublicChallenges(
        currentLocation: GeoLocation,
        context: Context,
        radiusInM: Double = 15000.0
    ) =
        repository.getPublicChallenges(
            currentLocation,
            radiusInM,
            context,
            forceFetchFromCloud = true
        )

}