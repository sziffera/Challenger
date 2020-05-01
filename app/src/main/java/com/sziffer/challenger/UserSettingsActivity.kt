package com.sziffer.challenger

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_user_settings.*

class UserSettingsActivity : AppCompatActivity() {

    private var username: String? = null
    private var email: String? = null
    private var weight = 0
    private var height = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_settings)


        //TODO(store them in sharedpref, only allow update when connected to internet)
        if (FirebaseManager.isUserValid) {

            FirebaseManager.currentUserRef?.child("username")?.addValueEventListener(object :
                ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    Log.i("FIREBASE", p0.toString())
                    username = p0.getValue(String::class.java) as String
                    usernameEditText.setText(username)
                }
            })

            FirebaseManager.currentUserRef?.child("email")?.addValueEventListener(object :
                ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    Log.i("FIREBASE", p0.toString())
                    email = p0.getValue(String::class.java) as String
                    emailEditText.setText(email)
                }
            })
            FirebaseManager.currentUserRef?.child("weight")?.addValueEventListener(object :
                ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    Log.i("FIREBASE", p0.toString())
                    weight = p0.getValue(Int::class.java) as Int
                    weightEditText.hint = "${weight}kg"
                    if (height != 0)
                        calculateAndSetBmi()
                }
            })
            FirebaseManager.currentUserRef?.child("height")?.addValueEventListener(object :
                ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    Log.i("FIREBASE", p0.toString())
                    height = p0.getValue(Int::class.java) as Int
                    if (weight != 0)
                        calculateAndSetBmi()
                    heightEditText.hint = "${height}cm"
                }
            })

        }
    }

    @SuppressLint("SetTextI18n")
    private fun calculateAndSetBmi() {
        val heightInMetres: Double = height.div(100.0)
        val bmi = weight.div(heightInMetres * heightInMetres)
        bmiTextView.text = getStringFromNumber(1, bmi)
        bmiInfoTextView.text = getBmiInfo(bmi)
    }

    private fun getBmiInfo(bmi: Double): String {

        return when (bmi) {
            in 0.0..18.5 -> getString(R.string.underweight)
            in 18.5..24.9 -> getString(R.string.normal)
            in 25.0..29.9 -> getString(R.string.overweight)
            else -> getString(R.string.obese)
        }
    }

    private fun updateUserData() {


    }
}
