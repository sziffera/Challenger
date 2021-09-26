package com.sziffer.challenger.database

import com.sziffer.challenger.model.challenge.PublicChallenge

interface PublicChallengeDbHelper {
    suspend fun getAll(): List<PublicChallenge>
    suspend fun insertAll(challenges: List<PublicChallenge>)
}