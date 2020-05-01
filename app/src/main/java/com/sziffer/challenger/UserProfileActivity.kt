package com.sziffer.challenger

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_user_profile.*

class UserProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        initUi()
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        dbHelper = ChallengeDbHelper(this)
        val list = dbHelper.getAllChallenges()
        dbHelper.close()
        var totalKm = 0.0
        var cycling = 0.0
        var running = 0.0
        for (item in list) {

            if (item.type == getString(R.string.running)) {
                running += item.dst
            } else {
                cycling += item.dst
            }
            totalKm += item.dst
        }

        val percentage = cycling.div(totalKm).times(100)
        statsProgressBar.progress = percentage.toInt().also {
            Log.i("user", "$it is the percentage")
        }

        totalKmTextView.text = getStringFromNumber(1, totalKm)
        totalCyclingTextView.text = "${getStringFromNumber(1, cycling)} km"
        totalRunningTextView.text = "${getStringFromNumber(1, running)} km"

        userSettingsButton.setOnClickListener {
            startActivity(Intent(this, UserSettingsActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initUi() {
        if (FirebaseManager.isUserValid) {
            FirebaseManager.currentUserRef!!.child("username")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        Log.e("USER", p0.toString())
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        welcomeUserTextView.text =
                            "${getString(R.string.hey)}, ${p0.value.toString()}!"
                        Log.i("USER", p0.value.toString())
                    }
                })
        } else {
            welcomeUserTextView.text = getString(R.string.hey) + "!"
        }

        if (FirebaseManager.isUserValid) {
            signInSignOutButton.setOnClickListener {
                FirebaseManager.mAuth.signOut()
                startLoginScreen()
            }
        } else {
            signInSignOutButton.text = getString(R.string.create_an_account)
            signInSignOutButton.setOnClickListener {
                startLoginScreen()
            }

        }
    }

    private fun startLoginScreen() {
        startActivity(
            Intent(this, LoginActivity::class.java)
        )
        finish()
    }
}
