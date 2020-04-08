package com.example.challenger.sync

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.example.challenger.ChallengeDbHelper
import com.example.challenger.SplashScreenActivity
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataSyncAsyncTask : AsyncTask<Context, String, String>() {

    private lateinit var challengeReference: DatabaseReference
    private var dbHelper: ChallengeDbHelper? = null

    override fun onPostExecute(result: String?) {
        if (dbHelper != null)
            dbHelper!!.close()
    }

    override fun onPreExecute() {
        challengeReference = SplashScreenActivity.usersDatabase.child("challenges")

    }

    override fun doInBackground(vararg params: Context): String {

        val sharedPref = params[0].getSharedPreferences(KEY_SYNC, Context.MODE_PRIVATE)
        val stringData = sharedPref.getString(KEY_SYNC_DATA, null)

        if (stringData == null) {

            return "ok"

        } else {

            dbHelper = ChallengeDbHelper(params[0])
            val typeJson = object : TypeToken<HashMap<String, String>>() {}.type
            val mMap = Gson().fromJson<HashMap<String, String>>(stringData, typeJson).also {
                Log.i(this::class.java.simpleName, "the map is: $it")
            }

            for (item in mMap) {
                if (item.value == "delete") {
                    //remove the challenge
                    challengeReference.child(item.key).removeValue()
                } else {
                    //upload the challenge
                    val challenge = dbHelper!!.getChallenge(item.key.toInt())
                    challengeReference.child(item.key).setValue(challenge)
                }
            }

            with(sharedPref.edit()) {
                clear()
                apply()
            }

            return "ok"
        }


    }


}