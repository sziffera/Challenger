package com.example.challenger

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class ChallengeDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_details)
        //TODO(flag to indicate the visibility of save button)

        val intent = intent
        val challenge = intent.getParcelableExtra("challenge") as? Challenge
        Log.i("CHALLENGE DETAILS",challenge.toString())
    }
}
