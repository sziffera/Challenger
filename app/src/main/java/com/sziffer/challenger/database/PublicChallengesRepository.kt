package com.sziffer.challenger.database

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.sziffer.challenger.State
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


class PublicChallengesRepository {

    private val publicChallengesCollection = FirebaseFirestore.getInstance().collection(
        PUBLIC_CHALLENGES_COLLECTION
    )

    @ExperimentalTime
    fun addPublicChallenge(challenge: PublicChallenge, context: Context) =
        flow<State<DocumentReference>> {

            emit(State.loading())

            val firstItem = challenge.route!!.first()
            val center = GeoLocation(firstItem.lat, firstItem.lng)

            getPublicChallenges(center, 500.0, context).collect { state ->
                when (state) {
                    is State.Success -> {
                        state.data.forEach {
                            check(isChallengeValid(it, challenge)) { "Challenge is not valid" }
                        }
                    }
                    is State.Loading -> Log.d(TAG, "Loading")
                    is State.Failed -> Log.e(TAG, state.message)
                }
            }

            val ref = publicChallengesCollection.add(challenge).await()

            emit(State.success(ref))


        }.catch {
            Log.e("UPLOAD", it.message ?: "no error message")
            emit(State.failed(it.localizedMessage ?: "no error message"))
        }.flowOn(Dispatchers.IO)

    private fun isChallengeValid(
        cloudChallenge: PublicChallenge,
        currentChallenge: PublicChallenge
    ): Boolean {

        // checking the type, just for safety
        if (currentChallenge.type != cloudChallenge.type) return false

        // checking length
        val validLength =
            currentChallenge.distance * 0.95 > cloudChallenge.distance
                    && currentChallenge.distance < cloudChallenge.distance * 1.05
        if (!validLength) return false

        val cloudChallengeLatLng = cloudChallenge.route?.map { LatLng(it.lat, it.lng) }
        // checking if all points of the current route are on the cloud challenge's path
        currentChallenge.route?.forEach {
            if (!PolyUtil.isLocationOnPath(
                    LatLng(it.lat, it.lng),
                    cloudChallengeLatLng,
                    true,
                    20.0
                )
            ) return false
        }

        return true
    }

    @ExperimentalTime
    fun getPublicChallenges(
        center: GeoLocation,
        radiusInM: Double,
        context: Context,
        forceFetchFromCloud: Boolean = false
    ) =
        flow {

            emit(State.loading())

            var challenges = fetchLocalChallenges(context, center)

            // the challenges are too old or the location is not ok or we force fetch from cloud
            if (challenges == null || forceFetchFromCloud) {
                challenges = ArrayList()
                val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
                bounds.forEach {
                    val query = publicChallengesCollection
                        .orderBy(GEOHASH_FIELD)
                        .limit(20)
                        .startAt(it.startHash)
                        .endAt(it.endHash)
                    val snapshot = query.get().await()
                    challenges!!.addAll(snapshot.toObjects(PublicChallenge::class.java))
                }

                // filtering out false positive results by checking the distance
                challenges = challenges.filter {
                    val location = GeoLocation(it.lat, it.lng)
                    val distanceInM = GeoFireUtils.getDistanceBetween(location, center)
                    distanceInM <= radiusInM
                } as ArrayList<PublicChallenge>

                emit(State.success(challenges))

            } else {
                // the local challenges are ok, passing back the result
                emit(State.success(challenges))
            }

        }.catch {
            // something went wrong
            emit(State.failed(it.message.toString()))
        }.flowOn(Dispatchers.IO)


    /**
     * Fetches the local public challenges if they are not too old and the current location
     * is almost the same than the location when the public challenges were saved
     */

    @ExperimentalTime
    private suspend fun fetchLocalChallenges(
        context: Context,
        location: GeoLocation
    ): ArrayList<PublicChallenge>? {

        // getting the saved values from sharedPreferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val lat = sharedPreferences.getFloat(KEY_LAT, -1f)
        val lng = sharedPreferences.getFloat(KEY_LNG, -1f)
        val date = sharedPreferences.getString(KEY_DATE, null)
        if (date == null || lng == -1f || lat == -1f) return null

        // checking the date difference
        val downloadDate = Constants.challengeDateFormat.parse(date)
        val currentDate = Date()
        val timeDifferenceInMinutes =
            milliseconds((currentDate.time - (downloadDate?.time ?: 0))).inWholeMinutes
        if (timeDifferenceInMinutes > 60) return null

        // checking the distance difference
        val downloadLocation = GeoLocation(lat.toDouble(), lng.toDouble())
        val distanceInMetres = GeoFireUtils.getDistanceBetween(location, downloadLocation)
        if (distanceInMetres > 3000) return null

        // the local data is fresh enough and the location was almost the same
        val dbHelperImpl =
            PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        return dbHelperImpl.getAll() as ArrayList<PublicChallenge>
    }


    companion object {
        private const val PUBLIC_CHALLENGES_COLLECTION = "challenges"
        private const val ROUTES_COLLECTION = "routes"
        private const val GEOHASH_FIELD = "geohash"

        private const val TAG = "REPO"

        private const val KEY_LNG = "com.sziffer.challenger.lng"
        private const val KEY_LAT = "com.sziffer.challenger.lat"
        private const val KEY_DATE = "com.sziffer.challenger.date"

    }
}
