package com.example.challenger

import android.app.IntentService
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException

class ChallengerService : IntentService(ChallengerService::class.java.simpleName) {


    private var route: ArrayList<MyLocation>? = null

    override fun onHandleIntent(intent: Intent?) {

        Log.i(TAG, "started")
        val stringRoute = intent?.getStringExtra(CHALLENGE_ROUTE)
        val time = intent?.getDoubleExtra(CHALLENGE_TIME, 0.0)
        try {
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            route = Gson().fromJson<ArrayList<MyLocation>>(stringRoute, typeJson)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
        }


        //TODO(convert route to array)

        object : Thread() {
            override fun run() {
                try {
                    if (route != null) {
                        for (i in route!!) {

                            val broadcastIntent = Intent(LocationUpdatesService.CHALLENGE_BROADCAST)
                                .putExtra(TIME_BROADCAST, i.time)
                                .putExtra(DISTANCE_BROADCAST, i.distance)

                            LocalBroadcastManager.getInstance(this@ChallengerService)
                                .sendBroadcast(broadcastIntent)
                            sleep(route!!.size.div(time!!).toLong())
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.e(TAG, e.toString())
                }
            }
        }.start()

        Log.i(TAG, "OnHandlerIntent() ended")
    }

    override fun onDestroy() {
        Log.i(TAG, "destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChallengerService"
        const val CHALLENGE_ROUTE = "challengeRoute"
        const val CHALLENGE_TIME = "challengeTime"
        const val TIME_BROADCAST = "$TAG.timeBroadcast"
        const val DISTANCE_BROADCAST = "$TAG.distanceBroadcast"
    }

}