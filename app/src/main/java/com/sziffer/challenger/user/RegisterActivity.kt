package com.sziffer.challenger.user

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.MainActivity
import com.sziffer.challenger.R
import com.sziffer.challenger.isEmailAddressValid
import kotlinx.android.synthetic.main.activity_register.*

class RegisterActivity : AppCompatActivity(), NetworkStateListener {

    private lateinit var nameText: EditText
    private lateinit var emailText: EditText
    private lateinit var passwordText: EditText
    private lateinit var registerButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var myNetworkCallback: MyNetworkCallback
    private var connected = true

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        sharedPreferences = getPreferences(0)

        userManager = UserManager(applicationContext)

        nameText = findViewById(R.id.registerUsernameEditText)
        emailText = findViewById(R.id.registerEmailEditText)
        passwordText = findViewById(R.id.registerPasswordEditText)
        registerButton = findViewById(R.id.registerButton)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)

        registerButton.setOnClickListener {
            createAccount(emailText.text.toString(), passwordText.text.toString())
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

    private fun createAccount(email: String, password: String) {

        if (!connected) {
            noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (password.length < 6) {
            passwordText.error = "Password must be at least 6 characters"
            passwordText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (!email.isEmailAddressValid()) {
            emailText.error = "Please provide a valid email address"
            emailText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }
        registerButton.isEnabled = false
        registerButton.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.fade_out
            )
        )

        FirebaseManager.mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) { // Sign in success

                    val user = User(
                        nameText.text.toString(),
                        FirebaseManager.mAuth.currentUser?.email.toString()
                    )

                    //saving user data to SharedPreferences
                    userManager.email = user.email
                    userManager.username = user.username

                    //setting the user's display name
                    FirebaseManager.mAuth.currentUser!!.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(user.username).build()
                    ).addOnSuccessListener {
                        Log.i("REGISTER", "display name set successfully")
                    }

                    //creating a child for the user
                    /* FirebaseManager.currentUserRef?.setValue(user)?.addOnSuccessListener {
                         Log.i("REGISTER", "userdata added to realtime database")
                     }

                     */

                    FirebaseDatabase.getInstance().getReference("users").child(
                        FirebaseAuth.getInstance().currentUser?.uid!!
                    ).setValue(user).addOnSuccessListener {
                        Log.i("REGISTER", "userdata added to realtime database")
                    }

                    startActivity(Intent(this, MainActivity::class.java))
                    Toast.makeText(this, "Successful sign-up", Toast.LENGTH_SHORT).show()
                    finish()

                } else { // If sign in failed
                    Toast.makeText(this, "Unsuccessful sign-up", Toast.LENGTH_SHORT).show()
                    registerButton.isEnabled = true
                    registerButton.startAnimation(
                        AnimationUtils.loadAnimation(
                            this,
                            R.anim.fade_in
                        )
                    )
                }

            }
    }

    override fun connectedToInternet() {
        connected = true
        noInternetTextView.visibility = View.GONE
    }

    override fun noInternetConnection() {
        connected = false
        noInternetTextView.visibility = View.VISIBLE
    }

}
