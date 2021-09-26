package com.sziffer.challenger.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.database.PublicChallengesRepository

class NearbyChallengesViewModel(private val repository: PublicChallengesRepository) : ViewModel() {

    fun getPublicChallenges(currentLocation: GeoLocation, radiusInM: Double, context: Context) =
        repository.getPublicChallenges(currentLocation, radiusInM, context)

}