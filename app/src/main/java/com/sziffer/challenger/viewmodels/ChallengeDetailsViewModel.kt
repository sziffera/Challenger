package com.sziffer.challenger.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sziffer.challenger.model.challenge.MyLocation

class ChallengeDetailsViewModel : ViewModel() {

    private val _route = MutableLiveData<ArrayList<MyLocation>>()
    val route: LiveData<ArrayList<MyLocation>> get() = _route

    private val _elevationGained = MutableLiveData<Int>()
    val elevationGained: LiveData<Int> get() = _elevationGained

    private val _elevationLoss = MutableLiveData<Int>()
    val elevationLoss: LiveData<Int> get() = _elevationLoss

    fun setRoute(route: ArrayList<MyLocation>) {
        this._route.value = route
    }

    fun setElevationData(gained: Int, loss: Int) {
        _elevationGained.postValue(gained)
        _elevationLoss.postValue(loss)
    }


    companion object {
        val shared = ChallengeDetailsViewModel()
    }
}