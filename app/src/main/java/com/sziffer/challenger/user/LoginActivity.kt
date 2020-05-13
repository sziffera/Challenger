package com.sziffer.challenger.user

import android.app.ActivityOptions
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.sziffer.challenger.MainActivity
import com.sziffer.challenger.R
import com.sziffer.challenger.isEmailAddressValid
import kotlinx.android.synthetic.main.activity_login.*


class LoginActivity : AppCompatActivity(), NetworkStateListener {


    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var mRef: DatabaseReference

    private lateinit var sharedPreferences: SharedPreferences


    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var mGoogleSignInOptions: GoogleSignInOptions

    private lateinit var loginButton: Button
    private lateinit var emailText: EditText
    private lateinit var passwordText: EditText
    private lateinit var registerRedirectButton: Button
    private lateinit var skipButton: Button

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager
    private var connected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        mRef = database.getReference("users")

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(
            this, connectivityManager
        )

        sharedPreferences = getSharedPreferences(MainActivity.UID_SHARED_PREF, Context.MODE_PRIVATE)

        emailText = findViewById(R.id.loginEmailText)
        passwordText = findViewById(R.id.loginPasswordText)
        loginButton = findViewById(R.id.loginButton)
        registerRedirectButton = findViewById(R.id.registerRedirectButton)
        skipButton = findViewById(R.id.skipButton)

/*
        configureGoogleSignIn()
        googleSignInButton.setOnClickListener {
            signIn()
        }
 */

        registerRedirectButton.setOnClickListener {
            val intent = Intent(applicationContext, RegisterActivity::class.java)
            startActivity(intent)
        }

        forgotPassworButton.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        loginButton.setOnClickListener {
            login(emailText.text.toString(), passwordText.text.toString())
        }
        skipButton.setOnClickListener {
            startMainActivity()
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

    private fun login(email: String, password: String) {

        if (!connected) {
            noInternetTextView.startAnimation(
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
                "Please fill the required fields",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        loginButton.isEnabled = false
        loginButton.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.fade_out
            )
        )
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(applicationContext, "Successful login!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                buttonShake()
                loginButton.isEnabled = true
                loginButton.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.fade_in
                    )
                )
                Toast.makeText(applicationContext, "Sign-in failed", Toast.LENGTH_SHORT).show()

            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    firebaseAuthWithGoogle(account)
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed:(", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buttonShake() {
        emailText.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.shake
            )
        )
        passwordText.startAnimation(
            AnimationUtils.loadAnimation(
                this,
                R.anim.shake
            )
        )
    }


    private fun configureGoogleSignIn() {
        mGoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, mGoogleSignInOptions)
    }

    private fun signIn() {
        val signInIntent: Intent = mGoogleSignInClient.signInIntent
        startActivityForResult(
            signInIntent,
            RC_SIGN_IN
        )
    }

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
        /*
        val key = mRef.push().key
        mRef.child(key!!).setValue(User()).addOnCompleteListener {
            if(it.isSuccessful) {
                Log.i("LOGIN",it.toString())
            }
            else {
                Log.i("LOGIN",it.exception.toString())
            }
        }

         */
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
        noInternetTextView.visibility = View.VISIBLE
    }

    override fun connectedToInternet() {
        noInternetTextView.visibility = View.GONE
    }

}
