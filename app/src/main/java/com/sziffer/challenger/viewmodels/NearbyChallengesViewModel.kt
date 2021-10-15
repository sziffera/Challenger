package com.sziffer.challenger.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.database.PublicChallengesRepository
import com.sziffer.challenger.utils.Constants

class NearbyChallengesViewModel(private val repository: PublicChallengesRepository) : ViewModel() {

    fun getPublicChallenges(
        currentLocation: GeoLocation,
        context: Context,
        radiusInM: Double = Constants.PublicChallenge.RADIUS_NEARBY_CHALLENGES
    ) =
        repository.getPublicChallenges(
            currentLocation,
            radiusInM,
            context,
            forceFetchFromCloud = true
        )

}