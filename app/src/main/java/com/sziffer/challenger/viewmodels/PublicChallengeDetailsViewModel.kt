package com.sziffer.challenger.viewmodels

import androidx.lifecycle.ViewModel
import com.sziffer.challenger.database.PublicChallengesRepository

class PublicChallengeDetailsViewModel(private val repository: PublicChallengesRepository) :
    ViewModel() {

    fun getChallenge(id: String) = repository.getChallenge(id)

}