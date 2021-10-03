package com.sziffer.challenger.database

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import com.sziffer.challenger.State
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.model.challenge.PublicChallengeHash
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.extensions.toGoogleLatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt


class PublicChallengesRepository {

    private val publicChallengesCollection = FirebaseFirestore.getInstance().collection(
        PUBLIC_CHALLENGES_COLLECTION
    )


    fun addPublicChallenge(
        challenge: PublicChallengeHash,
        context: Context,
        filter: Boolean = true
    ) =
        flow<State<DocumentReference>> {

            emit(State.loading())

            // filter is turned off in case of test uploads
            if (filter) {

                val firstItem = challenge.publicChallenge.route!!.first()
                val center = GeoLocation(firstItem.latLng.latitude, firstItem.latLng.longitude)

                getPublicChallenges(
                    center,
                    5000.0,
                    context,
                    forceFetchFromCloud = true
                ).collect { state ->
                    when (state) {
                        is State.Success -> {
                            Log.d(TAG, "Challenges fetched successfully, ${state.data.count()}")
                            state.data.forEach {
                                check(
                                    !isChallengeSimilarToOther(
                                        it,
                                        challenge.publicChallenge
                                    )
                                ) { "Challenge is not valid" }
                            }
                            val ref = publicChallengesCollection.add(challenge).await()
                            emit(State.success(ref))
                        }
                        is State.Loading -> Log.d(TAG, "Loading")
                        is State.Failed -> Log.e(TAG, state.message)
                    }
                }
            } else {
                val ref = publicChallengesCollection.add(challenge).await()
                emit(State.success(ref))
            }


        }.catch {
            Log.e("UPLOAD", it.message ?: "no error message")
            emit(State.failed(it.localizedMessage ?: "no error message"))
        }.flowOn(Dispatchers.IO)

    /**
     * Checks if the new activity followed the chosen challenge's route
     * returns false if not, true otherwise
     */
    private fun isChallengeValid(
        cloudChallenge: PublicChallenge,
        currentChallenge: PublicChallenge
    ): Boolean {

        // checking the type, just for safety
        if (currentChallenge.type != cloudChallenge.type) return false
        Log.d(TAG, "Challenge check: type is ok")

        // checking length
        val validLength =
            currentChallenge.distance * 0.95 > cloudChallenge.distance
                    && currentChallenge.distance < cloudChallenge.distance * 1.05
        if (!validLength) return false
        Log.d(TAG, "Challenge check: length is ok")

        val cloudChallengeLatLng = currentChallenge.route?.map { it.latLng.toGoogleLatLng() }
        // checking if all points of the current route are on the cloud challenge's path
        currentChallenge.route?.forEach {
            if (!PolyUtil.isLocationOnPath(
                    it.latLng.toGoogleLatLng(),
                    cloudChallengeLatLng,
                    true,
                    20.0
                )
            ) return false
        }
        Log.d(TAG, "Challenge check: similarity is ok")

        return true
    }


    private fun isChallengeSimilarToOther(
        cloudChallenge: PublicChallenge,
        currentChallenge: PublicChallenge
    ): Boolean {
        // checking the type, just for safety
        if (currentChallenge.type != cloudChallenge.type) return false
        Log.d(TAG, "Challenge check: type is ok")

        // checking length
        val validLength =
            currentChallenge.distance * 0.95 > cloudChallenge.distance
                    && currentChallenge.distance < cloudChallenge.distance * 1.05
        if (!validLength) return false
        Log.d(TAG, "Challenge check: length is ok")

        val cloudChallengeLatLng = currentChallenge.route?.map { it.latLng.toGoogleLatLng() }
        // checking if all points of the current route are on the cloud challenge's path
        var pointsOnPath = 0
        currentChallenge.route?.forEach {
            if (PolyUtil.isLocationOnPath(
                    it.latLng.toGoogleLatLng(),
                    cloudChallengeLatLng,
                    true,
                    20.0
                )
            ) pointsOnPath++
        }
        val pointsOnPathRate = pointsOnPath.toDouble() / (currentChallenge.route?.count()
            ?.toDouble() ?: 1.0)
        val isSimilar = pointsOnPathRate > 0.80

        Log.d(
            TAG,
            "Challenge check: similarity check is ${pointsOnPathRate.roundToInt()}%, so the check is $isSimilar"
        )

        return isSimilar


    }

    fun getPublicChallenges(
        center: GeoLocation,
        radiusInM: Double,
        context: Context,
        forceFetchFromCloud: Boolean = false
    ) =
        flow {

            emit(State.loading())

            Log.d(TAG, "Challenges started fetching")

            var challenges = fetchLocalChallenges(context, center)

            // the challenges are too old or the location is not ok or we force fetch from cloud
            if (challenges == null || challenges.isEmpty() || forceFetchFromCloud) {
                Log.d(TAG, "Challenges from cloud")
                challenges = ArrayList()
                val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
                Log.d(TAG, "Bounds length: ${bounds.count()}")
                bounds.forEach { bound ->
                    val query = publicChallengesCollection
                        .orderBy(GEOHASH_FIELD)
                        .startAt(bound.startHash)
                        .endAt(bound.endHash)
                    val snapshot = query.get().await()
                    challenges!!.addAll(
                        snapshot.toObjects(PublicChallengeHash::class.java)
                            .map { it.getPublicChallenge })
                }
//                val snapshot = publicChallengesCollection.get().await()
//                challenges.addAll(snapshot.toObjects(PublicChallenge::class.java))

                Log.d(TAG, "${challenges.count()} challenges fetched, filtering out false+")

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
            (currentDate.time - (downloadDate?.time ?: 0)) / 1000 / 60
        Log.d(TAG, "Time difference from last fetch? $timeDifferenceInMinutes")
        if (timeDifferenceInMinutes > 60) return null

        // checking the distance difference
        val downloadLocation = GeoLocation(lat.toDouble(), lng.toDouble())
        val distanceInMetres = GeoFireUtils.getDistanceBetween(location, downloadLocation)
        Log.d(TAG, "Distance to last fetch location: $distanceInMetres")
        if (distanceInMetres > 3000) return null

        // the local data is fresh enough and the location was almost the same
        Log.d(TAG, "Local DB is ok, getting challenges")
        val dbHelperImpl =
            PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        return dbHelperImpl.getAll() as ArrayList<PublicChallenge>
    }


    companion object {
        const val PUBLIC_CHALLENGES_COLLECTION = "challenges"
        private const val ROUTES_COLLECTION = "routes"
        private const val GEOHASH_FIELD = "geohash"

        private const val TAG = "REPO"

        private const val KEY_LNG = "com.sziffer.challenger.lng"
        private const val KEY_LAT = "com.sziffer.challenger.lat"
        private const val KEY_DATE = "com.sziffer.challenger.date"

    }
}
