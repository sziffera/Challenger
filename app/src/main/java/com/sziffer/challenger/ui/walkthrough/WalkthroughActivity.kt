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

        val fragments = arrayListOf(WalkthroughFragment())


    }


    private fun initWalkthroughFragment(
        title: String,
        description: String,
        image: String
    ): WalkthroughFragment {

        val bundle = Bundle().apply {
            putString(WalkthroughFragment.KEY_DESCRIPTION, description)
            putString(WalkthroughFragment.KEY_IMAGE, image)
            putString(WalkthroughFragment.KEY_TITLE, title)
        }

        return WalkthroughFragment().apply {
            arguments = bundle
        }

    }
}