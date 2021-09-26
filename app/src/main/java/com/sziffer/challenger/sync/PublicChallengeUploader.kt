package com.sziffer.challenger.sync

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.model.challenge.ChallengeType
import com.sziffer.challenger.model.challenge.MyLocation
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.reduceArrayLength
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class PublicChallengeUploader(
    private val appContext: Context,
    private val workerParams: WorkerParameters
) :
    Worker(appContext, workerParams) {


    override fun doWork(): Result {
        val challengeId = inputData.getInt(KEY_CHALLENGE_ID, -1)
        val userId = inputData.getString(KEY_USER_ID)
        val geohash = inputData.getString(KEY_GEOHASH)
        val typeInt = inputData.getInt(KEY_TYPE, 0)
        val type = if (typeInt == ChallengeType.CYCLING.ordinal)
            ChallengeType.CYCLING
        else
            ChallengeType.RUNNING
        val dbHelper = ChallengeDbHelper(appContext)
        val challenge = dbHelper.getChallenge(challengeId)
        if (challenge == null) return Result.failure()
        else {
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            val route = Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
            val elevationArray = DoubleArray(route!!.size)
            var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
            if (windowSize > Constants.MAX_WINDOW_SIZE)
                windowSize = Constants.MAX_WINDOW_SIZE
            Log.d("ELEVATION", "the calculated window size is: $windowSize")
            val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
            val filteredElevation = s1.smoothSignal()
            var elevGain = 0.0
            for (i in 0..filteredElevation.size - 2) {
                if (filteredElevation[i] < filteredElevation[i + 1])
                    elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
            }
            val reducedRoute = reduceArrayLength(route, challenge.dst * 1000.0)
            val publicChallenge: PublicChallenge
            challenge.apply {
                publicChallenge = PublicChallenge(
                    firebaseId,
                    route[0].latLng.latitude,
                    route[0].latLng.longitude,
                    userId!!,
                    geohash!!,
                    dst,
                    dur,
                    elevGain.roundToInt(),
                    type,
                    Date(),
                    reducedRoute
                )
            }


        }


        return Result.success()


    }


    companion object {
        const val KEY_CHALLENGE_ID = "challengeId"
        const val KEY_GEOHASH = "geohash"
        const val KEY_USER_ID = "userId"
        const val KEY_TYPE = "type"
    }


}