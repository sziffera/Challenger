package com.sziffer.challenger.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ChallengeDetailsViewModel : ViewModel() {

    private val _route = MutableLiveData<ArrayList<MyLocation>>()
    val route: LiveData<ArrayList<MyLocation>> get() = _route

    fun setRoute(route: ArrayList<MyLocation>) {
        this._route.value = route
    }


}