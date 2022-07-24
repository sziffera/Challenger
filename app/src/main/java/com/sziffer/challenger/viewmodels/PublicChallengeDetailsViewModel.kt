package com.sziffer.challenger.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sziffer.challenger.State
import com.sziffer.challenger.database.PublicChallengesRepository
import com.sziffer.challenger.model.challenge.PublicChallenge
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PublicChallengeDetailsViewModel(private val repository: PublicChallengesRepository) :
    ViewModel() {

    private val _challenge = MutableLiveData<State<PublicChallenge>>()
    val challenge: LiveData<State<PublicChallenge>> get() = _challenge

    fun insertChallengeToRoom(challenge: PublicChallenge, context: Context) =
        repository.addPublicChallenge(challenge, context)

    fun getChallenge(id: String) {
        viewModelScope.launch {
            repository.getChallenge(id).collect { state ->
                when (state) {
                    is State.Loading -> _challenge.value = State.loading()
                    is State.Success -> _challenge.value = State.success(state.data)
                    is State.Failed -> _challenge.value = State.failed(state.message)
                }
            }
        }
    }

}