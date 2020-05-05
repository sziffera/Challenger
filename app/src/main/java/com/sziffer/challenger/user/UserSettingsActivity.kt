package com.sziffer.challenger.user

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.R
import com.sziffer.challenger.getStringFromNumber
import kotlinx.android.synthetic.main.activity_user_settings.*

class UserSettingsActivity : AppCompatActivity(), NetworkStateListener {

    private var username: String? = null
    private var email: String? = null
    private var weight = 0
    private var height = 0


    private var connected: Boolean = true
    private var isDataDownloaded: Boolean = false

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_settings)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)
        getUserInfo()
        updateProfileButton.setOnClickListener {
            updateUserData()
        }
    }


    override fun onStart() {

        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
            noInternetTextView.visibility = View.VISIBLE
        }
        myNetworkCallback.registerCallback()
        super.onStart()
    }

    override fun onStop() {
        myNetworkCallback.unregisterCallback()
        super.onStop()

    }

    /** downloads user data from Firebase and sets the values of text views. */
    private fun getUserInfo() {
        //TODO(get data from local storage, if not exists, download)
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
                    //if height is already downloaded, calls calculate bmi method
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
                    //if weight is already downloaded, calls calculate bmi method
                    if (weight != 0)
                        calculateAndSetBmi()
                    heightEditText.hint = "${height}cm"
                }
            })

        }
    }

    @SuppressLint("SetTextI18n")
    private fun calculateAndSetBmi() {
        isDataDownloaded = true
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

    /** updates user data based on input and uploads it to Firebase */
    private fun updateUserData() {


        if (!connected) {
            noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

    }

    override fun noInternetConnection() {
        connected = false
        noInternetTextView.visibility = View.VISIBLE
    }

    override fun connectedToInternet() {
        connected = true
        noInternetTextView.visibility = View.GONE
    }

    companion object {
        private val TAG = UserSettingsActivity::class.java.simpleName
    }

}
