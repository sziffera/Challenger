package com.sziffer.challenger.sync

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.model.challenge.Challenge
import com.sziffer.challenger.model.user.UserManager
import java.util.concurrent.CountDownLatch

class DataDownloaderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) :
    Worker(appContext, workerParams) {

    /** Downloads new challenges from Firebase and saves them to local DB */
    override fun doWork(): Result {

        val dbHelper = ChallengeDbHelper(appContext)
        val userManager = UserManager(appContext)
        var success = true
        val countDownLatch = CountDownLatch(1)

        Log.i("WM", "WorkManager started")

        FirebaseManager.currentUserRef?.child("username")
            ?.addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        //do nothing
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        userManager.username = p0.value.toString()
                    }
                }
            )

        FirebaseManager.currentUserRef?.child("email")
            ?.addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        //do nothing
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        userManager.email = p0.value.toString()
                    }
                }
            )

        FirebaseManager.currentUserRef?.child("weight")
            ?.addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        //do nothing
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        val weight = p0.getValue(Int::class.java)
                        if (weight != null) {
                            userManager.weight = weight
                        }
                    }
                }
            )

        FirebaseManager.currentUserRef?.child("height")
            ?.addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        //do nothing
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        val height = p0.getValue(Int::class.java)
                        if (height != null) {
                            userManager.height = height
                        }
                    }
                }
            )

        FirebaseManager.currentUsersChallenges?.addValueEventListener(object :
            ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                success = false
                Log.e(this@DataDownloaderWorker::class.java.simpleName, p0.details)

            }

            override fun onDataChange(p0: DataSnapshot) {
                for (data in p0.children) {
                    val key = data.key.toString()
                    if (dbHelper.getChallengeByFirebaseId(key) == null) {

                        val challenge: Challenge? = data.getValue(Challenge::class.java)
                        if (challenge?.firebaseId?.isEmpty()!!) {
                            challenge.firebaseId = challenge.id
                        }

                        //TODO: set back
                        //dbHelper.addChallenge(challenge)
                    }
                }
                countDownLatch.countDown()
                dbHelper.close()
            }
        })
        if (success) {
            try {
                countDownLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        return if (success) Result.success() else
            Result.retry()
    }
}