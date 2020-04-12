package com.sziffer.challenger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object FirebaseManager {

    private val mDatabase = FirebaseDatabase.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    val mAuth = FirebaseAuth.getInstance()
    val currentUserRef: DatabaseReference?
        get() {
            return if (userId != null) {
                mDatabase.getReference("users").child(userId)
            } else
                null
        }

    val currentUsersChallenges: DatabaseReference? = currentUserRef?.child("challenges")

    val isUserValid: Boolean
        get() {
            return userId != null
        }

    val publicChallenges: DatabaseReference?
        get() {
            return if (userId != null) {
                mDatabase.getReference("challenges")
            } else
                null
        }
}