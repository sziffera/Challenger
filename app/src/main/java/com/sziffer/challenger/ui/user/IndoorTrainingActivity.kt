package com.sziffer.challenger.ui.user

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sziffer.challenger.databinding.ActivityIndoorTrainingBinding

class IndoorTrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIndoorTrainingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIndoorTrainingBinding.inflate(layoutInflater)


    }
}