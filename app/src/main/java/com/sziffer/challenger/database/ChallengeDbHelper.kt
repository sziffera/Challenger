package com.sziffer.challenger.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.preference.PreferenceManager
import com.sziffer.challenger.model.challenge.Challenge

class ChallengeDbHelper(val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE IF NOT EXISTS $DATABASE_NAME (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_FIREBASE_ID TEXT," +
                "$KEY_DATE TEXT," +
                "$KEY_NAME TEXT," +
                "$KEY_TYPE TEXT," +
                "$KEY_DISTANCE TEXT," +
                "$KEY_MAX_SPEED TEXT," +
                "$KEY_AVG_SPEED TEXT," +
                "$KEY_DURATION TEXT," +
                "$KEY_ELEVATION_GAIN," +
                "$KEY_ELEVATION_LOSS," +
                "$KEY_STRING_ROUTE TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        if (oldVersion == 2 && newVersion == 3) {

            db.execSQL("ALTER TABLE $DATABASE_NAME ADD COLUMN $KEY_ELEVATION_GAIN TEXT")
            db.execSQL("ALTER TABLE $DATABASE_NAME ADD COLUMN $KEY_ELEVATION_LOSS TEXT")
            db.execSQL("UPDATE $DATABASE_NAME SET $KEY_ELEVATION_GAIN = 0")
            db.execSQL("UPDATE $DATABASE_NAME SET $KEY_ELEVATION_LOSS = 0")

//        val challenges = getAllChallenges()
//        val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
//        for (challenge in challenges) {
//            val route = Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
//            val elevationArray = DoubleArray(route!!.size)
//            var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
//            if (windowSize > Constants.MAX_WINDOW_SIZE)
//                windowSize = Constants.MAX_WINDOW_SIZE
//            Log.d("ELEVATION", "the calculated window size is: $windowSize")
//            val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
//            val filteredElevation = s1.smoothSignal()
//            var elevGain = 0.0
//            var elevLoss = 0.0
//            for (i in 0..filteredElevation.size - 2) {
//                if (filteredElevation[i] < filteredElevation[i + 1])
//                    elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
//                else elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
//            }
//            challenge.elevGain = elevGain.roundToInt()
//            challenge.elevLoss = elevLoss.roundToInt()
//            updateChallenge(challenge.id.toInt(), challenge)
//        }
//
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            sharedPref.edit()
                .putBoolean(KEY_MIGRATION_DONE, true)
                .apply()
        } else {
            //todo
        }
    }

    fun addChallenge(challenge: Challenge): Long {
        val db: SQLiteDatabase = this.writableDatabase
        val contentValues: ContentValues = ContentValues()
        with(contentValues) {
            put(KEY_NAME, challenge.name)
            put(KEY_TYPE, challenge.type)
            put(KEY_DATE, challenge.date)
            put(KEY_FIREBASE_ID, challenge.firebaseId)
            put(KEY_DISTANCE, challenge.dst)
            put(KEY_DURATION, challenge.dur)
            put(KEY_AVG_SPEED, challenge.avg)
            put(KEY_MAX_SPEED, challenge.mS)
            put(KEY_STRING_ROUTE, challenge.routeAsString)
            put(KEY_ELEVATION_GAIN, challenge.elevGain)
            put(KEY_ELEVATION_LOSS, challenge.elevLoss)
        }
        Log.i(TAG, "Challenge wiht id ${challenge.id} was added")
        return db.insert(DATABASE_NAME, null, contentValues)
    }

    fun getAllChallenges(): ArrayList<Challenge> {

        val query = "SELECT * FROM $DATABASE_NAME"
        val db = writableDatabase
        val cursor = db.rawQuery(query, null)
        val challenges: ArrayList<Challenge> = ArrayList()
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast) {
                val challenge = with(cursor) {
                    Challenge(
                        getInt(getColumnIndex(KEY_ID)).toString(),
                        getString(getColumnIndex(KEY_FIREBASE_ID)),
                        getString(getColumnIndex(KEY_DATE)),
                        getString(getColumnIndex(KEY_NAME)),
                        getString(getColumnIndex(KEY_TYPE)),
                        getDouble(getColumnIndex(KEY_DISTANCE)),
                        getDouble(getColumnIndex(KEY_MAX_SPEED)),
                        getDouble(getColumnIndex(KEY_AVG_SPEED)),
                        getLong(getColumnIndex(KEY_DURATION)),
                        getString(getColumnIndex(KEY_STRING_ROUTE)),
                        getInt(getColumnIndex(KEY_ELEVATION_GAIN)),
                        getInt(getColumnIndex(KEY_ELEVATION_LOSS))
                    )
                }
                challenges.add(challenge)
                cursor.moveToNext()
            }
        }

        cursor.close()
        return challenges
    }

    fun getChallenge(id: Int): Challenge? {
        val db: SQLiteDatabase = this.readableDatabase
        val cursor = db.query(
            DATABASE_NAME,
            arrayOf(
                KEY_ID,
                KEY_FIREBASE_ID,
                KEY_DATE,
                KEY_TYPE,
                KEY_NAME,
                KEY_DURATION,
                KEY_DISTANCE,
                KEY_MAX_SPEED,
                KEY_AVG_SPEED,
                KEY_STRING_ROUTE,
                KEY_ELEVATION_GAIN,
                KEY_ELEVATION_LOSS
            ),
            "$KEY_ID=?",
            arrayOf(
                id.toString()
            ),
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            return with(cursor) {
                Challenge(
                    getInt(getColumnIndex(KEY_ID)).toString(),
                    getString(getColumnIndex(KEY_FIREBASE_ID)),
                    getString(getColumnIndex(KEY_DATE)),
                    getString(getColumnIndex(KEY_NAME)),
                    getString(getColumnIndex(KEY_TYPE)),
                    getDouble(getColumnIndex(KEY_DISTANCE)),
                    getDouble(getColumnIndex(KEY_MAX_SPEED)),
                    getDouble(getColumnIndex(KEY_AVG_SPEED)),
                    getLong(getColumnIndex(KEY_DURATION)),
                    getString(getColumnIndex(KEY_STRING_ROUTE)),
                    getInt(getColumnIndex(KEY_ELEVATION_GAIN)),
                    getInt(getColumnIndex(KEY_ELEVATION_LOSS))
                )
            }
        }
        cursor.close()
        return null
    }

    fun updateChallenge(id: Int, challenge: Challenge): Int {
        val db: SQLiteDatabase = this.writableDatabase
        val contentValues: ContentValues = ContentValues()
        with(contentValues) {
            put(KEY_NAME, challenge.name)
            put(KEY_FIREBASE_ID, challenge.firebaseId)
            put(KEY_DATE, challenge.date)
            put(KEY_DISTANCE, challenge.dst)
            put(KEY_DURATION, challenge.dur)
            put(KEY_AVG_SPEED, challenge.avg)
            put(KEY_MAX_SPEED, challenge.mS)
            put(KEY_STRING_ROUTE, challenge.routeAsString)
            put(KEY_ELEVATION_GAIN, challenge.elevGain)
            put(KEY_ELEVATION_LOSS, challenge.elevLoss)
        }
        Log.i(TAG, "challenge with id: $id was updated")
        return db.update(DATABASE_NAME, contentValues, "$KEY_ID = ?", arrayOf(id.toString()))
    }

    fun getChallengeByFirebaseId(firebaseId: String): Challenge? {

        val db: SQLiteDatabase = this.readableDatabase
        val cursor = db.query(
            DATABASE_NAME,
            arrayOf(
                KEY_ID,
                KEY_FIREBASE_ID,
                KEY_DATE,
                KEY_TYPE,
                KEY_NAME,
                KEY_DURATION,
                KEY_DISTANCE,
                KEY_MAX_SPEED,
                KEY_AVG_SPEED,
                KEY_STRING_ROUTE,
                KEY_ELEVATION_GAIN,
                KEY_ELEVATION_LOSS
            ),
            "$KEY_FIREBASE_ID=?",
            arrayOf(
                firebaseId
            ),
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            return with(cursor) {
                Challenge(
                    getInt(getColumnIndex(KEY_ID)).toString(),
                    getString(getColumnIndex(KEY_FIREBASE_ID)),
                    getString(getColumnIndex(KEY_DATE)),
                    getString(getColumnIndex(KEY_NAME)),
                    getString(getColumnIndex(KEY_TYPE)),
                    getDouble(getColumnIndex(KEY_DISTANCE)),
                    getDouble(getColumnIndex(KEY_MAX_SPEED)),
                    getDouble(getColumnIndex(KEY_AVG_SPEED)),
                    getLong(getColumnIndex(KEY_DURATION)),
                    getString(getColumnIndex(KEY_STRING_ROUTE)),
                    getInt(getColumnIndex(KEY_ELEVATION_GAIN)),
                    getInt(getColumnIndex(KEY_ELEVATION_LOSS))
                )
            }
        }

        cursor.close()

        return null

    }

    fun deleteDatabase() {
        val db: SQLiteDatabase = this.writableDatabase
        db.execSQL("delete from $DATABASE_NAME")
    }

    fun deleteChallenge(id: String): Boolean {

        val db: SQLiteDatabase = this.writableDatabase

        return db.delete(
            DATABASE_NAME,
            "$KEY_ID=?",
            arrayOf(id)
        ) > 0

    }


    companion object {
        private const val TAG = "ChallengeDbHelper"
        const val KEY_MIGRATION_DONE = "com.sziffer.challenger.Migration"
        const val DATABASE_VERSION = 3
        const val DATABASE_NAME = "MyChallenges"
        const val KEY_ID = "challengeId"
        const val KEY_NAME = "challengeName"
        const val KEY_AVG_SPEED = "avgSpeed"
        const val KEY_MAX_SPEED = "maxSpeed"
        const val KEY_DISTANCE = "distance"
        const val KEY_DURATION = "duration"
        const val KEY_STRING_ROUTE = "stringRoute"
        const val KEY_ELEVATION_GAIN = "elevationGain"
        const val KEY_ELEVATION_LOSS = "elevationLoss"
        const val KEY_DATE = "date"
        const val KEY_FIREBASE_ID = "firebaseId"
        const val KEY_TYPE = "type"
    }
}