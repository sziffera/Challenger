package com.sziffer.challenger.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.ui.user.LoginActivity

class SplashScreenActivity : AppCompatActivity() {

    companion object {

        private const val USERS = "users"
        private const val CHALLENGES = "challenges"

        private lateinit var mDatabase: FirebaseDatabase
        lateinit var usersDatabase: DatabaseReference
        lateinit var challengesDatabase: DatabaseReference

    }

    private lateinit var mAuth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDatabase = FirebaseDatabase.getInstance()


        usersDatabase = mDatabase.getReference(USERS)
        challengesDatabase = mDatabase.getReference(CHALLENGES)


        val userSharedPreferences = getSharedPreferences(
            MainActivity.UID_SHARED_PREF,
            Context.MODE_PRIVATE
        )

        mAuth = FirebaseAuth.getInstance()

        //decide whether the user has already opened the app
        if (mAuth.currentUser != null || userSharedPreferences.contains(MainActivity.NOT_REGISTERED)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        else {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()

        }



    }
}
