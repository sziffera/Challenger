package com.sziffer.challenger.sync

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.Challenge
import com.sziffer.challenger.ChallengeDbHelper
import com.sziffer.challenger.FirebaseManager

class DataDownloaderWorker(private val appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    //TODO(use childeventvaluelistener)

    override fun doWork(): Result {

        val dbHelper = ChallengeDbHelper(appContext)
        var success = true

        //TODO(use childEventValueListener)

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

                        dbHelper.addChallenge(challenge)
                    }
                }
                dbHelper.close()
            }
        })

        return if (success) Result.success() else
            Result.retry()
    }
}