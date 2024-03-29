package com.sziffer.challenger.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.Constants

@Dao
interface PublicChallengeDao {
    @Query("SELECT * FROM ${Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME}")
    suspend fun getAll(): List<PublicChallenge>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(challenges: List<PublicChallenge>)

    @Query("SELECT * FROM ${Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME} WHERE id=:challengeId")
    suspend fun getChallengeById(challengeId: String): PublicChallenge?

    @Query("DELETE FROM ${Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME}")
    suspend fun deleteAll()
}