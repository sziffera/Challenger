package com.sziffer.challenger.ui.walkthrough

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sziffer.challenger.databinding.ActivityWalkthroughBinding

class WalkthroughActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkthroughBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkthroughBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
}