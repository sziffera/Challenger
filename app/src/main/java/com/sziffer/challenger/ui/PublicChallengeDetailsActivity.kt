package com.sziffer.challenger.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sziffer.challenger.State
import com.sziffer.challenger.databinding.ActivityPublicChallengeDetailsBinding
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModel
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PublicChallengeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublicChallengeDetailsBinding
    private lateinit var viewModel: PublicChallengeDetailsViewModel

    // Coroutine Scope
    private val uiScope = CoroutineScope(Dispatchers.Main)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublicChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, PublicChallengeDetailsViewModelFactory()).get(
            PublicChallengeDetailsViewModel::class.java
        )

        val challengeId = intent.getStringExtra(KEY_CHALLENGE_ID)
        uiScope.launch {
            if (challengeId != null) {
                getChallenge(challengeId)
            }
        }
    }


    private suspend fun getChallenge(id: String) {
        viewModel.getChallenge(id).collect { state ->
            when (state) {
                is State.Loading -> {
                }
                is State.Success -> {
                }
                is State.Failed -> {
                }
            }
        }
    }


    companion object {
        const val KEY_CHALLENGE_ID = "challengeId"
    }


}