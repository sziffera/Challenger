package com.sziffer.challenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sziffer.challenger.database.PublicChallengesRepository

class NearbyChallengesViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(PublicChallengesRepository::class.java)
            .newInstance(PublicChallengesRepository())
    }
}