package com.example.challenger.sync

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

const val KEY_SYNC = "keySync"
const val KEY_SYNC_DATA = "$KEY_SYNC.data"
const val KEY_UPLOAD = "upload"
const val KEY_DELETE = "delete"

private fun startWorkManager(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
    WorkManager.getInstance(context).enqueue(workRequest)
}

fun updateSharedPrefForSync(context: Context, id: String, whatToDo: String) {

    val sharedPref = context.getSharedPreferences(KEY_SYNC, Context.MODE_PRIVATE)
    val stringData = sharedPref.getString(KEY_SYNC_DATA, null)

    val mMap: MutableMap<String, String>?

    mMap = if (stringData != null) {
        val typeJson = object : TypeToken<HashMap<String, String>>() {}.type
        Gson().fromJson<HashMap<String, String>>(stringData, typeJson)
    } else {
        HashMap()
    }

    mMap?.set(id, whatToDo)

    val newStringData = Gson().toJson(mMap)
    with(sharedPref.edit()) {
        putString(KEY_SYNC_DATA, newStringData)
        apply()
    }

    startWorkManager(context)

}