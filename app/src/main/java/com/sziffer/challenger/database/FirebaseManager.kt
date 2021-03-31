package com.sziffer.challenger.database

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object FirebaseManager {

    private val mDatabase = FirebaseDatabase.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    val mAuth = FirebaseAuth.getInstance()

    /** returns with the reference of the current user */
    val currentUserRef: DatabaseReference?
        get() {
            return if (userId != null) {
                mDatabase.getReference("users").child(
                    userId
                )
            } else
                null
        }


    val currentUsersChallenges: DatabaseReference? = currentUserRef?.child("challenges")

    /** returns whether the user is logged in or not */
    val isUserValid: Boolean
        get() {
            return FirebaseAuth.getInstance().currentUser != null
        }

    /**
     * maybe later, public challenges will be available for all users. They can share their
     * recorded challenges to public as well.
     * */
    val publicChallenges: DatabaseReference?
        get() {
            return if (userId != null) {
                mDatabase.getReference("challenges")
            } else
                null
        }
}