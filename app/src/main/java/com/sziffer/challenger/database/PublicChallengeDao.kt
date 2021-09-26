package com.sziffer.challenger.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.Constants

@Dao
interface PublicChallengeDao {
    @Query("SELECT * FROM ${Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME}")
    suspend fun getAll(): List<PublicChallenge>

    @Insert
    suspend fun insertAll(challenges: List<PublicChallenge>)

}