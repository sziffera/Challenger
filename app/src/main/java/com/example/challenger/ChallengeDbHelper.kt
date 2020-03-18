package com.example.challenger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChallengeDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE IF NOT EXISTS $DATABASE_NAME (" +
                "$KEY_ID INTEGER(10) PRIMARY KEY," +
                "$KEY_NAME TEXT," +
                "$KEY_AVG_SPEED TEXT," +
                "$KEY_DISTANCE TEXT," +
                "$KEY_DURATION TEXT," +
                "$KEY_MAX_SPEED TEXT," +
                "$KEY_ROUTE TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $DATABASE_NAME")
        onCreate(db)
    }

    fun addChallenge(challenge: Challenge) {
        val db: SQLiteDatabase = this.writableDatabase
        val contentValues: ContentValues = ContentValues()
        with(contentValues) {
            put(KEY_NAME,challenge.n)
            put(KEY_ID, challenge.id)
            put(KEY_DISTANCE, challenge.dst)
            put(KEY_DURATION, challenge.dur)
            put(KEY_AVG_SPEED, challenge.avg)
            put(KEY_MAX_SPEED, challenge.mS)
            put(KEY_ROUTE, challenge.stringRoute)
        }
        db.insert(DATABASE_NAME, null, contentValues)
        db.close()
    }

    fun updateChallenge(challenge: Challenge) : Int {
        val db: SQLiteDatabase = this.writableDatabase
        val contentValues: ContentValues = ContentValues()
        with(contentValues) {
            put(KEY_NAME,challenge.n)
            put(KEY_DISTANCE, challenge.dst)
            put(KEY_DURATION, challenge.dur)
            put(KEY_AVG_SPEED, challenge.avg)
            put(KEY_MAX_SPEED, challenge.mS)
            put(KEY_ROUTE, challenge.stringRoute)
        }

        return db.update(DATABASE_NAME,contentValues,"$KEY_ID = ?", arrayOf(challenge.id))
    }


    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "UserChallenges.db"
        const val KEY_ID = "challengeId"
        const val KEY_NAME = "challengeName"
        const val KEY_AVG_SPEED = "avgSpeed"
        const val KEY_MAX_SPEED = "maxSpeed"
        const val KEY_DISTANCE = "distance"
        const val KEY_DURATION = "duration"
        const val KEY_ROUTE = "route"
    }
}