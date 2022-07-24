package com.sziffer.challenger.database

import android.content.Context
import androidx.room.Room

object PublicChallengeDbBuilder {

    private var instance: PublicChallengesDatabase? = null

    fun getInstance(context: Context): PublicChallengesDatabase {
        if (instance == null) {
            synchronized(PublicChallengesDatabase::class) {
                instance = buildRoomDb(context)
            }
        }
        return instance!!
    }

    private fun buildRoomDb(context: Context) =
        Room.databaseBuilder(
            context.applicationContext,
            PublicChallengesDatabase::class.java,
            "public-challenges"
        ).build()
}