package com.sziffer.challenger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sziffer.challenger.State
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.PublicChallengesRepository
import com.sziffer.challenger.utils.extensions.toPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Gets the local challenge and converts it to a PublicChallenge
 * Uploads the PublicChallenge to Firestore if it meets with the criteria
 */
class PublicChallengeUploader(
    private val appContext: Context,
    workerParams: WorkerParameters
) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val challengeId = inputData.getString(KEY_CHALLENGE_ID)

        if (challengeId != null) {
            val dbHelper = ChallengeDbHelper(appContext)
            val challenge = dbHelper.getChallenge(challengeId.toInt())
            val publicChallenge = challenge?.toPublic(appContext)
            if (publicChallenge?.userId == "no_id") {
                publicChallenge.userId = inputData.getString(KEY_USER_ID) ?: "no_id"
            }
            val repo = PublicChallengesRepository()
            repo.addPublicChallenge(publicChallenge!!, appContext).collect { state ->
                when (state) {
                    is State.Success -> Result.success()
                    is State.Failed -> Result.retry()
                    is State.Loading -> {}
                }
            }
            Result.success()
        }
        Result.failure()
    }


    companion object {
        const val KEY_CHALLENGE_ID = "challengeId"
        const val KEY_USER_ID = "userId"
    }
}