package com.sziffer.challenger.user

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sziffer.challenger.R
import com.sziffer.challenger.isEmailAddressValid
import kotlinx.android.synthetic.main.activity_forgot_password.*

class ForgotPasswordActivity : AppCompatActivity(), NetworkStateListener {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var connectionCallback: MyNetworkCallback
    private var connected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        connectionCallback = MyNetworkCallback(this, connectivityManager)

        sendPasswordResetEmailButton.setOnClickListener {
            sendPasswordResetEmail()
        }
    }

    override fun onStart() {
        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
            noInternetTextView.visibility = View.VISIBLE
        }
        connectionCallback.registerCallback()
        super.onStart()
    }

    override fun onStop() {
        connectionCallback.unregisterCallback()
        super.onStop()
    }

    private fun sendPasswordResetEmail() {

        if (!connected) {
            noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (!resetPasswordEmailEditText.text.toString().isEmailAddressValid()) {
            resetPasswordEmailEditText.error = getString(R.string.invalid_email)
            resetPasswordEmailEditText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        FirebaseManager.mAuth.sendPasswordResetEmail(resetPasswordEmailEditText.text.toString())
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, getString(R.string.email_sent), Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this, LoginActivity::class.java),
                        ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                    )
                } else {
                    resetPasswordEmailEditText.startAnimation(
                        AnimationUtils.loadAnimation(
                            this,
                            R.anim.shake
                        )
                    )
                    Toast.makeText(
                        this,
                        getString(R.string.cant_send_email),
                        Toast.LENGTH_LONG
                    ).show()
                }

            }
    }

    override fun connectedToInternet() {
        runOnUiThread {
            connected = true
            noInternetTextView.visibility = View.GONE
        }

    }

    override fun noInternetConnection() {
        runOnUiThread {
            connected = false
            noInternetTextView.visibility = View.VISIBLE
        }
    }
}
