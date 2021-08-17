package com.sziffer.challenger.ui.user

import android.app.ActivityOptions
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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityLoginBinding
import com.sziffer.challenger.model.User
import com.sziffer.challenger.model.UserManager
import com.sziffer.challenger.ui.MainActivity
import com.sziffer.challenger.utils.MyNetworkCallback
import com.sziffer.challenger.utils.NetworkStateListener
import com.sziffer.challenger.utils.isEmailAddressValid


class LoginActivity : AppCompatActivity(), NetworkStateListener {


    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var mRef: DatabaseReference

    private lateinit var sharedPreferences: SharedPreferences


    //private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var mGoogleSignInOptions: GoogleSignInOptions

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

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(
            this, connectivityManager
        )

        sharedPreferences = getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)



        binding.registerRedirectButton.setOnClickListener {
            val intent = Intent(applicationContext, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.forgotPassworButton.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        binding.loginButton.setOnClickListener {
            login(binding.loginEmailText.text.toString(), binding.loginPasswordText.text.toString())
        }
        binding.skipButton.setOnClickListener {
            startMainActivity()
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

                //TODO(set username)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
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

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == RC_SIGN_IN) {
//            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
//            try {
//                val account = task.getResult(ApiException::class.java)
//                if (account != null) {
//                    firebaseAuthWithGoogle(account)
//                }
//            } catch (e: ApiException) {
//                Toast.makeText(this, "Google sign in failed:(", Toast.LENGTH_LONG).show()
//            }
//        }
//    }

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


//    private fun configureGoogleSignIn() {
//        mGoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestIdToken(getString(R.string.default_web_client_id))
//            .requestEmail()
//            .build()
//        mGoogleSignInClient = GoogleSignIn.getClient(this, mGoogleSignInOptions)
//    }
//
//    private fun signIn() {
//        val signInIntent: Intent = mGoogleSignInClient.signInIntent
//        startActivityForResult(
//            signInIntent,
//            RC_SIGN_IN
//        )
//    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mAuth.signInWithCredential(credential).addOnCompleteListener {
            if (it.isSuccessful) {

                mAuth.currentUser!!.linkWithCredential(credential)
                    .addOnCompleteListener(
                        this
                    ) { task ->
                        if (task.isSuccessful) {

                            val userResult = task.result!!.user
                            Log.i("LINKED", userResult?.email.toString())

                        } else {

                        }
                    }
                val user =
                    User(email = mAuth.currentUser?.email.toString())
                FirebaseManager.currentUserRef?.setValue(user)?.addOnSuccessListener {
                    Log.i("REGISTER", "userdata added to realtime database")
                }

                startActivity(Intent(applicationContext, MainActivity::class.java))

                finish()
            } else {
                Toast.makeText(this, "Google sign in failed:(", Toast.LENGTH_LONG).show()
            }
        }
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


    companion object {
        private const val RC_SIGN_IN: Int = 1
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

}
