package com.example.challenger

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)

        if(!sharedPreferences.contains("offline")) {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            with(sharedPreferences.edit()) {
                this.putBoolean("offline",true)
                apply()
            }
        }



        mAuth = FirebaseAuth.getInstance()

        if (mAuth.currentUser != null || sharedPreferences.contains("offline user")) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        else {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()

        }



    }
}
