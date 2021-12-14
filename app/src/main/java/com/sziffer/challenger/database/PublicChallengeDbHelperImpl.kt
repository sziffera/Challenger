package com.sziffer.challenger.database

import com.sziffer.challenger.model.challenge.PublicChallenge

class PublicChallengeDbHelperImpl(private val database: PublicChallengesDatabase) :
    PublicChallengeDbHelper {

    override suspend fun getAll(): List<PublicChallenge> = database.challengeDao().getAll()

    override suspend fun insertAll(challenges: List<PublicChallenge>) =
        database.challengeDao().insertAll(challenges)

    override suspend fun getChallenge(id: String): PublicChallenge? =
        database.challengeDao().getChallengeById(id)

    override suspend fun deleteAll() {
        database.challengeDao().deleteAll()
    }
}