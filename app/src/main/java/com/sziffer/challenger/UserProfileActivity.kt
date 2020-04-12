package com.sziffer.challenger

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_user_profile.*

class UserProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        mAuth = FirebaseAuth.getInstance()
        val id = mAuth.currentUser?.uid
        val ref = FirebaseDatabase.getInstance().getReference("users").child(id!!)
        var name: String = ""
        ref.child("username").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                Log.e("USER", p0.toString())
            }

            override fun onDataChange(p0: DataSnapshot) {
                name = p0.value as String
                welcomeUserTextView.text = "HI ${p0.value.toString()}!"
                Log.i("USER", p0.value.toString())
            }

        })


    }

    override fun onStart() {
        super.onStart()
        dbHelper = ChallengeDbHelper(this)
        val list = dbHelper.getAllChallenges()
        var totalKm = 0.0
        var cycling = 0.0
        var running = 0.0
        for (item in list) {

            if (item.type == "running") {
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

    override fun onPause() {
        dbHelper.close()
        super.onPause()
    }
}
