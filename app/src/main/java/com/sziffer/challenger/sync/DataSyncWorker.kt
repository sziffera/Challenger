package com.sziffer.challenger.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.ChallengeDbHelper
import com.sziffer.challenger.FirebaseManager

class DataSyncWorker(private val appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {


    override fun doWork(): Result {


        val mRef = FirebaseManager.currentUserRef ?: return Result.retry()

        var success = true

        val sharedPref = appContext.getSharedPreferences(KEY_SYNC, 0)
        val stringData = sharedPref.getString(KEY_SYNC_DATA, null)

        if (stringData != null) {

            val dbHelper = ChallengeDbHelper(appContext)
            val typeJson = object : TypeToken<HashMap<String, String>>() {}.type
            val mMap = Gson().fromJson<HashMap<String, String>>(stringData, typeJson)

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