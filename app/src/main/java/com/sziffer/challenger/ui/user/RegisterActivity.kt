package com.sziffer.challenger.ui.user

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityRegisterBinding
import com.sziffer.challenger.model.User
import com.sziffer.challenger.model.UserManager
import com.sziffer.challenger.ui.MainActivity
import com.sziffer.challenger.utils.MyNetworkCallback
import com.sziffer.challenger.utils.NetworkStateListener
import com.sziffer.challenger.utils.isEmailAddressValid


class RegisterActivity : AppCompatActivity(), NetworkStateListener {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var myNetworkCallback: MyNetworkCallback
    private var connected = true

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getPreferences(0)

        userManager = UserManager(applicationContext)


        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)

        binding.registerButton.setOnClickListener {
            createAccount(
                binding.registerEmailEditText.text.toString(),
                binding.registerPasswordEditText.text.toString()
            )
        }

    }

    override fun onStart() {
        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
            binding.noInternetTextView.visibility = View.VISIBLE
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
            binding.noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (password.length < 6) {
            binding.registerPasswordEditText.error = getString(R.string.invalid_password)
            binding.registerPasswordEditText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (!email.isEmailAddressValid()) {
            binding.registerEmailEditText.error = getString(R.string.invalid_email)
            binding.registerEmailEditText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }
        binding.registerButton.isEnabled = false
        binding.registerButton.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.fade_out
            )
        )

        FirebaseManager.mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) { // Sign in success

                    val user = User(
                        binding.registerUsernameEditText.text.toString(),
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
                    Toast.makeText(this, getString(R.string.successful_sign_up), Toast.LENGTH_SHORT)
                        .show()
                    finish()

                } else { // If sign in failed
                    Toast.makeText(
                        this,
                        getString(R.string.unsuccessful_sing_up),
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.registerButton.isEnabled = true
                    binding.registerButton.startAnimation(
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
        runOnUiThread {
            binding.noInternetTextView.visibility = View.GONE
        }

    }

    override fun noInternetConnection() {
        connected = false
        runOnUiThread {
            binding.noInternetTextView.visibility = View.VISIBLE
        }

    }

}
