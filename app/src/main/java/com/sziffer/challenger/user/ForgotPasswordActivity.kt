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
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityForgotPasswordBinding
import com.sziffer.challenger.utils.MyNetworkCallback
import com.sziffer.challenger.utils.NetworkStateListener
import com.sziffer.challenger.utils.isEmailAddressValid


class ForgotPasswordActivity : AppCompatActivity(), NetworkStateListener {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var connectionCallback: MyNetworkCallback
    private var connected = true

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        connectionCallback = MyNetworkCallback(this, connectivityManager)

        binding.sendPasswordResetEmailButton.setOnClickListener {
            sendPasswordResetEmail()
        }
    }

    override fun onStart() {
        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
            binding.noInternetTextView.visibility = View.VISIBLE
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
            binding.noInternetTextView.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        if (!binding.resetPasswordEmailEditText.text.toString().isEmailAddressValid()) {
            binding.resetPasswordEmailEditText.error = getString(R.string.invalid_email)
            binding.resetPasswordEmailEditText.startAnimation(
                AnimationUtils.loadAnimation(
                    this,
                    R.anim.shake
                )
            )
            return
        }

        FirebaseManager.mAuth.sendPasswordResetEmail(binding.resetPasswordEmailEditText.text.toString())
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, getString(R.string.email_sent), Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this, LoginActivity::class.java),
                        ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                    )
                } else {
                    binding.resetPasswordEmailEditText.startAnimation(
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
            binding.noInternetTextView.visibility = View.GONE
        }

    }

    override fun noInternetConnection() {
        runOnUiThread {
            connected = false
            binding.noInternetTextView.visibility = View.VISIBLE
        }
    }
}
