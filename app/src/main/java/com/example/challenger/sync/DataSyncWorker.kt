package com.example.challenger.sync

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.challenger.ChallengeDbHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataSyncWorker(private val appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {


    override fun doWork(): Result {

        val mAuth = FirebaseAuth.getInstance()
        val mRef = FirebaseDatabase.getInstance().getReference("users").child(mAuth.uid.toString())
            .child("challenges")
        var success = true

        val sharedPref = appContext.getSharedPreferences(KEY_SYNC, 0)
        val stringData = sharedPref.getString(KEY_SYNC_DATA, null)

        if (stringData != null) {
            val dbHelper = ChallengeDbHelper(appContext)
            val typeJson = object : TypeToken<HashMap<String, String>>() {}.type
            val mMap = Gson().fromJson<HashMap<String, String>>(stringData, typeJson)

            Log.i(this::class.java.simpleName, mMap.toString())

            for (item in mMap) {
                if (item.value == KEY_DELETE) {
                    mRef.child(item.key).removeValue().addOnFailureListener {
                        success = false
                    }
                } else {
                    val challenge = dbHelper.getChallenge(item.key.toInt())
                    if (challenge != null) {
                        mRef.child(item.key).setValue(challenge).addOnFailureListener {
                            success = false
                        }
                    }
                }
            }
            with(sharedPref.edit()) {
                clear()
                apply()
            }
        }
        return if (success)
            Result.success()
        else
            Result.failure()
    }


}