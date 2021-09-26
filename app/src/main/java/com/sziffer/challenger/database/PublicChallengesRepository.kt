package com.sziffer.challenger.database

import android.content.Context
import androidx.preference.PreferenceManager
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FirebaseFirestore
import com.sziffer.challenger.State
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.model.challenge.PublicRouteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

class PublicChallengesRepository {

    private val publicChallengesCollection = FirebaseFirestore.getInstance().collection(
        PUBLIC_CHALLENGES_COLLECTION
    )
    private val publicRoutesCollection = FirebaseFirestore.getInstance().collection(
        ROUTES_COLLECTION
    )


    fun getPublicChallenges(center: GeoLocation, radiusInM: Double, context: Context) =
        flow {

            emit(State.loading())

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

            val lat = sharedPreferences.getFloat(KEY_LAT, -1f)
            val lng = sharedPreferences.getFloat(KEY_LNG, -1f)
            val date = sharedPreferences.getString(KEY_DATE, null)

            // if (lat != -1f && lng != -1f)


            val dbHelperImpl =
                PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))

            //TODO: get last refresh date, if older than one day get from Firestore else get from Room

            var challenges = dbHelperImpl.getAll() as ArrayList<PublicChallenge>

            if (challenges.isNotEmpty()) {

            } else {

            }

            val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)

            bounds.forEach {
                val query = publicChallengesCollection
                    .orderBy(GEOHASH_FIELD)
                    .limit(20)
                    .startAt(it.startHash)
                    .endAt(it.endHash)
                val snapshot = query.get().await()
                challenges.addAll(snapshot.toObjects(PublicChallenge::class.java))
            }

            challenges = challenges.filter {
                val location = GeoLocation(it.lat, it.lng)
                val distanceInM = GeoFireUtils.getDistanceBetween(location, center)
                distanceInM <= radiusInM
            } as ArrayList<PublicChallenge>

            emit(State.success(challenges))

        }.catch {
            emit(State.failed(it.message.toString()))
        }.flowOn(Dispatchers.IO)

    fun getRouteForChallenge(id: String) = flow {

        emit(State.loading())

        val query = publicRoutesCollection.whereEqualTo("id", id)
        val snapshot = query.get().await()
        val route = snapshot.toObjects(PublicRouteItem::class.java)
        emit(State.success(route.firstOrNull()))

    }.catch {
        emit(State.failed(it.message.toString()))
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val PUBLIC_CHALLENGES_COLLECTION = "challenges"
        private const val ROUTES_COLLECTION = "routes"
        private const val GEOHASH_FIELD = "geohash"

        private const val KEY_LNG = "com.sziffer.challenger.lng"
        private const val KEY_LAT = "com.sziffer.challenger.lat"
        private const val KEY_DATE = "com.sziffer.challenger.date"

    }
}
