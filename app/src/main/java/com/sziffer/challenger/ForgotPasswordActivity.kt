package com.sziffer.challenger

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_forgot_password.*

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        sendPasswordResetEmailButton.setOnClickListener {
            sendPasswordResetEmail()
        }
    }

    private fun sendPasswordResetEmail() {

        if (!resetPasswordEmailEditText.text.toString().isEmailAddressValid()) {
            resetPasswordEmailEditText.error = "Please provide a valid email address"
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
                    Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()
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
                        "Can't send email, make sure your email address is valid",
                        Toast.LENGTH_LONG
                    ).show()
                }

            }
    }
}
