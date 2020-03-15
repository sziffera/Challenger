package com.example.challenger

import com.google.firebase.database.FirebaseDatabase

class FirebaseHelper {
    companion object {

        private const val USERS = "users"
        private const val CHALLENGES = "challenges"

        private val mDatabase = FirebaseDatabase.getInstance()

        val usersDatabase = mDatabase.getReference(USERS)
        val challengesDatabase = mDatabase.getReference(CHALLENGES)


    }
}