package com.sziffer.challenger.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sziffer.challenger.model.challenge.PublicChallenge

@Database(entities = [PublicChallenge::class], version = 2)
@TypeConverters(Converters::class)
abstract class PublicChallengesDatabase : RoomDatabase() {
    abstract fun challengeDao(): PublicChallengeDao
}