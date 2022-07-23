package com.sziffer.challenger.viewmodels

import android.content.Context
import android.util.Log
import android.util.Range
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.State
import com.sziffer.challenger.database.PublicChallengesRepository
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.model.user.UserManager
import com.sziffer.challenger.utils.Constants
import kotlinx.coroutines.launch

class NearbyChallengesViewModel(private val repository: PublicChallengesRepository) : ViewModel() {

    var distanceRange: Range<Int> = Range.create(0, Constants.PublicChallenge.DISTANCE_FILTER_MAX)
        get() = Range(field.lower.times(1000), field.upper.times(1000))

    private var publicChallenges: ArrayList<PublicChallenge> = ArrayList()
    private val _publicChallenges = MutableLiveData<ArrayList<PublicChallenge>>()
    val publicChallengesLiveData: LiveData<ArrayList<PublicChallenge>>
        get() = _publicChallenges

    fun filterAndSetPublicChallenges(
        context: Context,
        useFilter: Boolean = true
    ) {
        if (useFilter) {
            val userManager = UserManager(context)
            Log.d(
                "NearbyChallenges",
                "Range is: $distanceRange, type is ${userManager.routesFilterChallengeType}"
            )
            val filteredPublicChallenges = publicChallenges.filter {
                (it.distance >= distanceRange.lower && it.distance <= distanceRange.upper)
            }
            Log.d(
                "NearbyChallenges",
                "filtered count is ${filteredPublicChallenges.count()}"
            )
            _publicChallenges.postValue(filteredPublicChallenges as ArrayList<PublicChallenge>?)
        } else
            _publicChallenges.postValue(publicChallenges)
    }

    fun getPublicChallenges(
        currentLocation: GeoLocation,
        context: Context,
        radiusInM: Double = Constants.PublicChallenge.RADIUS_NEARBY_CHALLENGES
    ) {
        viewModelScope.launch {
            repository.getPublicChallenges(
                currentLocation,
                radiusInM,
                context,
                forceFetchFromCloud = true
            ).collect { state ->
                when (state) {
                    is State.Loading -> {}
                    is State.Success -> {
                        publicChallenges = state.data
                        filterAndSetPublicChallenges(context, useFilter = false)
                    }
                    is State.Failed -> {
                        filterAndSetPublicChallenges(context, useFilter = false)
                    }
                }
            }
        }
    }
}
