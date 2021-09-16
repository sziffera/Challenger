package com.sziffer.challenger.ui.user

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityLoginBinding
import com.sziffer.challenger.model.UserManager
import com.sziffer.challenger.ui.MainActivity
import com.sziffer.challenger.utils.*


class LoginActivity : AppCompatActivity(), NetworkStateListener {


    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var mRef: DatabaseReference

    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var binding: ActivityLoginBinding

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager
    private var connected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        mRef = database.getReference("users")

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(
            this, connectivityManager
        )

        sharedPreferences = getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)

        binding.registerRedirectButton.setOnClickListener {
            val intent = Intent(applicationContext, RegisterActivity::class.java)
            startActivity(
                intent,
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        binding.forgotPasswordButton.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        binding.privacy.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://challenger-fitnessapp.sziffer.hu/")
                )
            )
        }

        binding.loginButton.setOnClickListener {
            login(binding.loginEmailText.text.toString(), binding.loginPasswordText.text.toString())
        }
        binding.skipButton.setOnClickListener {
            startMainActivity()
        }

        binding.googleSignInButton.setOnClickListener {
            googleSignIn()
        }

        val backgrounds = arrayListOf(
            ContextCompat.getDrawable(this, R.drawable.gradient_background),
            ContextCompat.getDrawable(this, R.drawable.gradient_background_2),
            ContextCompat.getDrawable(this, R.drawable.gradient_background_3),
            ContextCompat.getDrawable(this, R.drawable.gradient_background_4)
        )

        crossfade(binding.backgroundImageView, backgrounds, 5000, this)
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

    private fun googleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun login(email: String, password: String) {

        if (!connected) {
            binding.noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (password.isEmpty() || !email.isEmailAddressValid()) {
            buttonShake()
            Toast.makeText(
                applicationContext,
                getString(R.string.please_fill_required_fields),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        binding.loginButton.isEnabled = false
        binding.loginButton.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.fade_out
            )
        )
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                successfulSignIn()
            } else {
                buttonShake()
                binding.loginButton.isEnabled = true
                binding.loginButton.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.fade_in
                    )
                )
                Toast.makeText(
                    applicationContext,
                    getString(R.string.sing_in_failed),
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
    }


    private fun buttonShake() {
        binding.loginEmailText.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.shake
            )
        )
        binding.loginPasswordText.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.shake
            )
        )
    }

    private fun successfulSignIn() {
        Toast.makeText(
            applicationContext,
            getString(R.string.successful_login),
            Toast.LENGTH_SHORT
        ).show()

        UserManager(this).apply {
            username = FirebaseManager.mAuth.currentUser?.displayName.also {
                Log.d("LOGIN", it.toString())
            }
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun startMainActivity() {

        with(sharedPreferences.edit()) {

            putString(MainActivity.NOT_REGISTERED, System.currentTimeMillis().toString())
            apply()
        }


        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    successfulSignIn()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Google sign-in failed!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
            }
        }
    }


    override fun noInternetConnection() {
        runOnUiThread {
            connected = false
            binding.noInternetTextView.visibility = View.VISIBLE
        }

    }

    override fun connectedToInternet() {
        runOnUiThread {
            connected = true
            binding.noInternetTextView.visibility = View.GONE
        }

    }

    companion object {
        private const val RC_SIGN_IN = 9001
        const val TAG = "LoginActivity"
    }

}
