package com.sziffer.challenger.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sziffer.challenger.databinding.ActivityNearbyChallengesBinding
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory

class NearbyChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyChallengesBinding
    private lateinit var viewModel: NearbyChallengesViewModel

    private var recyclerViewAdapter: ChallengeRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyChallengesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel =
            ViewModelProvider(this, NearbyChallengesViewModelFactory()).get(
                NearbyChallengesViewModel::class.java
            )


    }
}