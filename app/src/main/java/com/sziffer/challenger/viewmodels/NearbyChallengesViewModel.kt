package com.sziffer.challenger.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.database.PublicChallengesRepository
import kotlin.time.ExperimentalTime

class NearbyChallengesViewModel(private val repository: PublicChallengesRepository) : ViewModel() {

    @ExperimentalTime
    fun getPublicChallenges(currentLocation: GeoLocation, radiusInM: Double, context: Context) =
        repository.getPublicChallenges(currentLocation, radiusInM, context)

}