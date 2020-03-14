package com.example.challenger

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_register.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var nameText: EditText
    private lateinit var emailText: EditText
    private lateinit var passwordText: EditText
    private lateinit var registerButton: Button
    private lateinit var database: FirebaseDatabase
    private lateinit var mRef: DatabaseReference
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        sharedPreferences = getPreferences(0)

        nameText = findViewById(R.id.registerUsernameEditText)
        emailText = findViewById(R.id.registerEmailEditText)
        passwordText = findViewById(R.id.registerPasswordEditText)
        registerButton = findViewById(R.id.registerButton)

        mAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        mRef = database.getReference("users")

        registerButton.setOnClickListener {
            createAccount(emailText.text.toString(),passwordText.text.toString())
        }

    }

    private fun createAccount(email: String, password: String) {

        if(password.length < 6) {
            passwordText.error = "Password must be at least 6 characters"
            return
        }

        if(!email.isEmailAddressValid()) {
            emailText.error = "Please provide a valid email address"
            return
        }

        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) { // Sign in success, update UI with the signed-in user's information


                    val user = User(nameText.text.toString(),mAuth.currentUser?.email.toString())
                    mRef.child(mAuth.currentUser?.uid.toString()).setValue(user).addOnSuccessListener {
                        Log.i("REGISTER","userdata added to realtime database")
                    }
                    Log.i("REGISTER",mAuth.currentUser?.getIdToken(true).toString())
                    val intent = Intent(applicationContext,MainActivity::class.java)
                    startActivity(intent)
                    Toast.makeText(applicationContext,"Successful sign-up",Toast.LENGTH_SHORT).show()
                    finish()

                } else { // If sign in fails, display a message to the user.
                    Toast.makeText(applicationContext,"Unsuccessful sign-up",Toast.LENGTH_SHORT).show()
                }

            }
    }

    private fun String.isEmailAddressValid(): Boolean {
        return this.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
    }

}
