package com.example.challenger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private const val OFFLINE = "offline"
    }

    private lateinit var mAuth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseSharedPreferences = getSharedPreferences(OFFLINE, Context.MODE_PRIVATE)

        //make firebase database available offline
        if(!firebaseSharedPreferences.contains(OFFLINE)) {
            Log.i("FIREBASE","PERSISTENCE")
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            with(firebaseSharedPreferences.edit()) {
                this.putBoolean(OFFLINE,true)
                commit()
            }
        }

        val userSharedPreferences = getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)



        mAuth = FirebaseAuth.getInstance()

        //decide whether the user has already opened the app
        if (mAuth.currentUser != null || userSharedPreferences.contains(MainActivity.NOT_REGISTERED)) {
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
