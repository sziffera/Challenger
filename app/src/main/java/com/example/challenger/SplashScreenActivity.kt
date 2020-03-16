package com.example.challenger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private const val OFFLINE = "offline"
        private const val USERS = "users"
        private const val CHALLENGES = "challenges"

        private lateinit var mDatabase: FirebaseDatabase
        lateinit var usersDatabase: DatabaseReference
        lateinit var challengesDatabase: DatabaseReference

    }

    private lateinit var mAuth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseSharedPreferences = getSharedPreferences(OFFLINE, Context.MODE_PRIVATE)

        //make firebase database available offline
        if(!firebaseSharedPreferences.contains(OFFLINE)) {
            mDatabase = FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }
            with(firebaseSharedPreferences.edit()) {
                this.putBoolean(OFFLINE,true)
                commit()
            }
        } else
            mDatabase = FirebaseDatabase.getInstance()


        usersDatabase = mDatabase.getReference(USERS).apply { keepSynced(true) }
        challengesDatabase = mDatabase.getReference(CHALLENGES).apply { keepSynced(true) }


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
