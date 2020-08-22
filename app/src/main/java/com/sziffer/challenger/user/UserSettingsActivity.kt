package com.sziffer.challenger.user

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.R
import com.sziffer.challenger.isEmailAddressValid
import kotlinx.android.synthetic.main.activity_user_settings.*

class UserSettingsActivity : AppCompatActivity(), NetworkStateListener {

    private var username: String? = null
    private var email: String? = null
    private var weight = 0
    private var height = 0

    private lateinit var userManager: UserManager

    private var connected: Boolean = true
    private var isDataDownloaded: Boolean = false

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_settings)
        userManager = UserManager(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)
        getUserInfo()
        updateProfileButton.setOnClickListener {
            updateUserData()
        }

        calculateBodyFatButton.setOnClickListener {
            startActivity(
                Intent(this, BodyFatCalculatorActivity::class.java)
            )
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

    override fun onResume() {
        initSettingsSwitches()
        super.onResume()
    }

    /** downloads user data from Firebase and sets the values of text views. */
    private fun getUserInfo() {

        if (FirebaseManager.isUserValid) {

            if (userManager.username != null) {
                username = userManager.username
                usernameEditText.setText(username)
            } else {

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
            }
            if (userManager.email != null) {
                emailEditText.setText(userManager.email)
            } else {
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
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun calculateAndSetBmi() {
        isDataDownloaded = true
        val heightInMetres: Double = height.div(100.0)
        val bmi = weight.div(heightInMetres * heightInMetres)
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
        if (!FirebaseManager.isUserValid) {
            return
        }
        //setting the username
        if (!username.equals(usernameEditText.text.toString()) && usernameEditText.text.isNotEmpty()) {
            userManager.username = usernameEditText.text.toString()
            username = usernameEditText.text.toString()
            FirebaseManager.mAuth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(username).build()
            )?.addOnSuccessListener {
                Log.i("REGISTER", "display name set successfully")
            }
            FirebaseManager.currentUserRef?.child("username")?.setValue(
                usernameEditText.text.toString()
            )
        }

        if (!email.equals(emailEditText.text.toString())
            && emailEditText.text.toString().isEmailAddressValid()
        ) {

            FirebaseManager.currentUserRef?.child("email")?.setValue(
                usernameEditText.text.toString()
            )
            userManager.email = emailEditText.text.toString()
            email = emailEditText.text.toString()
            FirebaseManager.mAuth.currentUser?.updateEmail(
                emailEditText.text.toString()
            )?.addOnSuccessListener {
                Log.i(TAG, "email update successful")
            }

        }


        Toast.makeText(this, R.string.update_success, Toast.LENGTH_SHORT).show()

    }

    override fun noInternetConnection() {
        runOnUiThread {
            connected = false
            noInternetTextView.visibility = View.VISIBLE
        }
    }

    override fun connectedToInternet() {
        runOnUiThread {
            connected = true
            noInternetTextView.visibility = View.INVISIBLE
        }
    }

    private fun initSettingsSwitches() {

        autoPauseSwitch.isChecked = userManager.autoPause

        //autoPauseSwitchCompat.isChecked = userManager.autoPause
        preventScreenLockSwitch.isChecked = userManager.preventScreenLock
        differenceSwitch.isChecked = userManager.difference
        avgSpeedSwitch.isChecked = userManager.avgSpeed
        durationSwitch.isChecked = userManager.duration
        distanceSwitch.isChecked = userManager.distance
        startStopSwitch.isChecked = userManager.startStop


        autoPauseSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.autoPause = isChecked
        }

        preventScreenLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.preventScreenLock = isChecked
        }
        startStopSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.startStop = isChecked
        }
        differenceSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.difference = isChecked
        }
        distanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.distance = isChecked
        }
        durationSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.duration = isChecked
        }
        avgSpeedSwitch.setOnCheckedChangeListener { _, isChecked ->
            userManager.avgSpeed = isChecked
        }
    }

    companion object {
        private val TAG = UserSettingsActivity::class.java.simpleName
    }

}
