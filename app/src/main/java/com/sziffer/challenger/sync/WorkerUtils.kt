package com.sziffer.challenger.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.database.FirebaseManager
import java.util.concurrent.TimeUnit

const val KEY_SYNC = "keySync"
const val KEY_SYNC_DATA = "$KEY_SYNC.data"
const val KEY_UPLOAD = "upload"
const val KEY_DELETE = "delete"
const val DATA_DOWNLOADER_TAG = "com.sziffer.challenger.DataDownloader"

/** helper class for creating  */
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

fun startPublicChallengeUploader(
    challengeId: Int,
    userId: String,
    geohash: String,
    type: Int,
    context: Context
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val workRequest = OneTimeWorkRequestBuilder<PublicChallengeUploader>()
        .setConstraints(constraints)
        .setInputData(
            Data.Builder()
                .putString(PublicChallengeUploader.KEY_GEOHASH, geohash)
                .putString(PublicChallengeUploader.KEY_USER_ID, userId)
                .putInt(PublicChallengeUploader.KEY_TYPE, type)
                .putInt(PublicChallengeUploader.KEY_CHALLENGE_ID, challengeId).build()

        )
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            OneTimeWorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
    WorkManager.getInstance(context).enqueue(workRequest)
}


fun startDataDownloaderWorkManager(context: Context) {
    Log.i("MAIN", "WM started from MainActivity")
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<DataDownloaderWorker>()
        .addTag(DATA_DOWNLOADER_TAG)
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
    WorkManager.getInstance(context).cancelAllWorkByTag(DATA_DOWNLOADER_TAG)
    WorkManager.getInstance(context).enqueue(workRequest)
}

/** this method updates the queue for uploading and starts the WorkManager */
fun updateSharedPrefForSync(context: Context, id: String, whatToDo: String) {

    //the user is not authorised
    if (!FirebaseManager.isUserValid) return

    val sharedPref = context.getSharedPreferences(KEY_SYNC, Context.MODE_PRIVATE)
    val stringData = sharedPref.getString(KEY_SYNC_DATA, null)

    val mMap: MutableMap<String, String>? = if (stringData != null) {
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