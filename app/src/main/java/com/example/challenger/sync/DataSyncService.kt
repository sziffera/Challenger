package com.example.challenger.sync

import android.app.job.JobParameters
import android.app.job.JobService

class DataSyncService : JobService() {

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        DataSyncAsyncTask().execute(applicationContext)
        return false
    }

}