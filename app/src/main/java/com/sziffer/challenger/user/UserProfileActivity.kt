package com.sziffer.challenger.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityUserProfileBinding
import com.sziffer.challenger.utils.getStringFromNumber
import java.util.*

class UserProfileActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var userManager: UserManager
    private var currentYear: Int = 0

    private lateinit var binding: ActivityUserProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userManager = UserManager(applicationContext)
        currentYear = Calendar.getInstance().get(Calendar.YEAR)
        Log.d("YEAR", currentYear.toString())
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
        var thisYear = 0.0
        for (item in list) {
            val year = item.date.subSequence(6, 10).toString().also {
                Log.d("YEAR", it)
            }
            try {
                if (year.toInt() == currentYear) {
                    thisYear += item.dst
                }
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }

            if (item.type == getString(R.string.running)) {
                running += item.dst
            } else {
                cycling += item.dst
            }
            totalKm += item.dst
        }

        val percentage = cycling.div(totalKm).times(100)
        binding.statsProgressBar.progress = percentage.toInt().also {
            Log.i("user", "$it is the percentage")
        }

        binding.thisYearKmTextView.text = "${getString(R.string.this_year)}:" +
                " ${getStringFromNumber(1, thisYear)} km"

        binding.totalKmTextView.text =
            getStringFromNumber(1, totalKm)
        binding.totalCyclingTextView.text = "${
            getStringFromNumber(
                1,
                cycling
            )
        } km"
        binding.totalRunningTextView.text = "${
            getStringFromNumber(
                1,
                running
            )
        } km"

        if (FirebaseManager.isUserValid) {

            binding.userSettingsButton.setOnClickListener {
                startActivity(Intent(this, UserSettingsActivity::class.java))
            }

        } else {
            binding.userSettingsButton.visibility = View.GONE
        }
        if (userManager.bmi != 0f)
            binding.bmiIndexTextView.text = getStringFromNumber(1, userManager.bmi)
        if (userManager.bodyFat != 0f)
            binding.bodyFatTextView.text = "${getStringFromNumber(1, userManager.bodyFat)}%"
    }

    @SuppressLint("SetTextI18n")
    private fun initUi() {
        if (FirebaseManager.isUserValid) {

            if (userManager.username != null) {
                binding.welcomeUserTextView.text =
                    "${getString(R.string.hey)}, ${userManager.username}"
            } else {
                FirebaseManager.currentUserRef!!.child("username")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            Log.e("USER", p0.toString())
                        }

                        override fun onDataChange(p0: DataSnapshot) {
                            binding.welcomeUserTextView.text =
                                "${getString(R.string.hey)}, ${p0.value.toString()}!"
                            Log.i("USER", p0.value.toString())
                            userManager.username = p0.value.toString()
                        }
                    })
            }
        } else {
            binding.welcomeUserTextView.text = getString(R.string.hey) + "!"
        }

        if (FirebaseManager.isUserValid) {
            binding.signInSignOutButton.setOnClickListener {
                FirebaseManager.mAuth.signOut()
                val dbHelper = ChallengeDbHelper(this)
                dbHelper.deleteDatabase()
                with(userManager) {
                    username = null
                    email = null
                    weight = 0
                    height = 0
                }
                dbHelper.close()
                startLoginScreen()
            }
        } else {
            binding.signInSignOutButton.text = getString(R.string.create_an_account)
            binding.signInSignOutButton.setOnClickListener {
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
