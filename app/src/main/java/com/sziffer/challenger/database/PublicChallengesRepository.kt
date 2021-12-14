package com.sziffer.challenger.database

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.sziffer.challenger.State
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.extensions.toHash
import com.sziffer.challenger.utils.extensions.toPublicChallenge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt


class PublicChallengesRepository {

    private val publicChallengesCollection = FirebaseFirestore.getInstance().collection(
        PUBLIC_CHALLENGES_COLLECTION
    )


    fun getChallenge(id: String) = flow {
        emit(State.loading())
        val snapshot = publicChallengesCollection.whereEqualTo("id", id).get().await()
        // if the first() throws NoSuchElementException it will be caught below
        val challenge = snapshot.first().data.toPublicChallenge()
        emit(State.success(challenge))
    }.catch {
        emit(State.failed(it.stackTraceToString()))
    }.flowOn(Dispatchers.IO)


    fun validateAndUploadChallenge(
        challengeId: String,
        cloudChallengeId: String,
        context: Context
    ) = flow<State<Boolean>> {
        emit(State.loading())

        val db = PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        val snapshot =
            publicChallengesCollection.whereEqualTo("id", cloudChallengeId).get().await()
        val challenge = db.getChallenge(challengeId)
        val cloudChallenge = snapshot.documents.firstOrNull()?.data?.toPublicChallenge()
        // checking validity and if the new one is faster
        check(cloudChallenge != null)
        check(challenge != null)
        check(isChallengeValid(cloudChallenge, challenge))
        check(isFaster(cloudChallenge, challenge))
        // this part throws an exception if the check is not true

        // we can get here only if the upper part is ok

        val reference = snapshot.documents.firstOrNull()?.reference
        // increasing the number of attempts
        reference?.update("attempts", FieldValue.increment(1))
        // updating the required fields
        reference?.update(
            mapOf(
                "timestamp" to challenge.timestamp,
                "route" to Gson().toJson(challenge.route),
                "duration" to challenge.duration,
                "user_id" to challenge.userId
            )
        )
    }.catch {
        emit(State.failed("The challenge is not valid or slower"))
    }.flowOn(Dispatchers.IO)

    private fun isFaster(cloudChallenge: PublicChallenge, challenge: PublicChallenge) =
        challenge.duration < cloudChallenge.duration


    fun addPublicChallenge(
        challenge: PublicChallenge,
        context: Context,
        filter: Boolean = true
    ) =
        flow<State<DocumentReference>> {
            emit(State.loading())
            // filter is turned off in case of test uploads
            if (filter) {
                // getting the first item's location, this is the starting point of the route
                val firstItem = challenge.route!!.first()
                val center = GeoLocation(firstItem.latLng.latitude, firstItem.latLng.longitude)

                // fetching challenges in RADIUS_UPLOAD_NEARBY from starting point
                getPublicChallenges(
                    center,
                    Constants.PublicChallenge.RADIUS_UPLOAD_NEARBY,
                    context,
                    forceFetchFromCloud = true
                ).collect { state ->
                    when (state) {
                        is State.Success -> {
                            Log.d(TAG, "Challenges fetched successfully, ${state.data.count()}")
                            // checking each nearby challenge if they are similar or not
                            state.data.forEach {
                                check(
                                    !isChallengeSimilarToOther(
                                        it,
                                        challenge
                                    )
                                ) { "Challenge is not valid" }
                            }
                            // increasing the nr of attempts
                            challenge.attempts++
                            // if the challenge is not similar to the cloud challenges, uploading
                            val ref = publicChallengesCollection.add(challenge.toHash()).await()
                            emit(State.success(ref))
                        }
                        is State.Loading -> Log.d(TAG, "Loading")
                        is State.Failed -> Log.e(TAG, state.message)
                    }
                }
            } else {
                // the filter is turned off, just uploading the challenge - FOR TESTING PURPOSES
                val ref = publicChallengesCollection.add(challenge.toHash()).await()
                emit(State.success(ref))
            }
        }.catch {
            Log.e("UPLOAD", it.message ?: "no error message")
            emit(State.failed(it.localizedMessage ?: "no error message"))
        }.flowOn(Dispatchers.IO)

    /**
     * Checks if the new activity followed the chosen challenge's route properly
     * returns false if not, true otherwise
     */
    private fun isChallengeValid(
        cloudChallenge: PublicChallenge,
        currentChallenge: PublicChallenge
    ): Boolean {

        // checking the type, just for safety, in theory it is impossible to happen
        if (currentChallenge.type != cloudChallenge.type) return false


        // checking length
        val validLength =
            currentChallenge.distance * (1.0 - DISTANCE_TOLERANCE_PERCENT) < cloudChallenge.distance
                    && currentChallenge.distance < cloudChallenge.distance * (1.0 + DISTANCE_TOLERANCE_PERCENT)
        if (!validLength) return false


        val cloudChallengeLatLng = cloudChallenge.route?.map { it.latLng }
        var pointsOnPath = 0
        // checking if all points of the current route are on the cloud challenge's path
        currentChallenge.route?.forEach {
            if (PolyUtil.isLocationOnPath(
                    it.latLng,
                    cloudChallengeLatLng,
                    true,
                    200.0
                )
            ) pointsOnPath++
        }
        val pointsOnPathPercent =
            currentChallenge.route!!.count().toDouble() / pointsOnPath.toDouble()
        Log.d(
            TAG,
            "isChallengeValid(): Points on path $pointsOnPath, similarity is ${pointsOnPathPercent * 100}"
        )
        return pointsOnPathPercent > 0.80
    }

    /**
     * We have to measure how many points of the current route are on the nearby cloud
     * challenge's route. We compare the current challenge route to the cloud challenge's route,
     * not vice versa. In this way, if the cloud challenge is longer, we can get a 100% match.
     * So after getting the similarity percent, we have to compare the distances as well to avoid
     * check fails with much shorter routes.
     */
    private fun isChallengeSimilarToOther(
        cloudChallenge: PublicChallenge,
        currentChallenge: PublicChallenge
    ): Boolean {
        // checking the type, just for safety
        if (currentChallenge.type != cloudChallenge.type) return false
        Log.d(TAG, "Challenge check: type is ok")

        val cloudChallengeLatLng = cloudChallenge.route?.map { it.latLng }
        // checking if all points of the current route are on the cloud challenge's path

        var pointsOnPath = 0
        currentChallenge.route?.forEach {
            if (PolyUtil.isLocationOnPath(
                    it.latLng,
                    cloudChallengeLatLng,
                    true,
                    TOLERANCE_IN_METRES
                )
            ) pointsOnPath++
        }
        val pointsOnPathRate = pointsOnPath.toDouble() / (currentChallenge.route!!.count()
            .toDouble())
        val isSimilar = pointsOnPathRate > 0.80

        Log.d(
            TAG,
            "Challenge check: similarity check is ${(pointsOnPathRate * 100.0).roundToInt()}%, points on path $pointsOnPath, so the check is $isSimilar"
        )

        // maybe length check should be added
        // checking length if the routes are really similar, not just overlap each other on a shorter interval
        if (isSimilar) {
            val routeLengthRate = currentChallenge.distance / cloudChallenge.distance
            val isLengthDifferent = routeLengthRate > 1.35 || routeLengthRate < 0.65
            Log.d(
                TAG,
                "Challenge check: length rate is $routeLengthRate, so isLengthDifferent is $isLengthDifferent"
            )
            // the length is different, accept this challenge
            if (isLengthDifferent) return false
        }
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

            // getting the local challenges if they are ok
            var challenges = ArrayList<PublicChallenge>()//fetchLocalChallenges(context, center)

            // the challenges are too old or the location is not ok or we force fetch from cloud
            if (challenges.isEmpty() || forceFetchFromCloud) {
                Log.d(TAG, "Challenges from cloud")
                challenges = ArrayList()
                val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
                bounds.forEach { bound ->
                    val query = publicChallengesCollection
                        .orderBy(GEOHASH_FIELD)
                        .startAt(bound.startHash)
                        .endAt(bound.endHash)
                    val snapshot = query.get().await()
                    snapshot.documents.forEach {
                        it.data?.let { challengeHashMap ->
                            challenges.add(challengeHashMap.toPublicChallenge())
                        }
                    }
                }

                Log.d(TAG, "${challenges.count()} challenges fetched, filtering out false+")

                // filtering out false positive results by checking the distance - very unlikely
                challenges = challenges.filter {
                    val location = GeoLocation(it.lat, it.lng)
                    val distanceInM = GeoFireUtils.getDistanceBetween(location, center)
                    distanceInM <= radiusInM
                } as ArrayList<PublicChallenge>

                insertChallenges(context, center, challenges)

                emit(State.success(challenges))

            } else {
                // the local challenges are ok, passing back the result
                emit(State.success(challenges))
            }

        }.catch {
            // something went wrong
            emit(State.failed(it.message.toString()))
        }.flowOn(Dispatchers.IO)


    private suspend fun insertChallenges(
        context: Context,
        location: GeoLocation,
        challenges: ArrayList<PublicChallenge>
    ) {
        val dbHelperImpl =
            PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        dbHelperImpl.deleteAll()
        dbHelperImpl.insertAll(challenges)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
        val currentDate: String = current.format(formatter)

        sharedPreferences.edit {
            putFloat(KEY_LAT, location.latitude.toFloat())
            putFloat(KEY_LNG, location.longitude.toFloat())
            putString(KEY_DATE, currentDate)
        }
        Log.d(
            TAG,
            "Local db updated successfully with ${challenges.count()} challenges on: $currentDate"
        )
    }


    /**
     * Fetches the local public challenges if they are not too old and the current location
     * is almost the same than the location when the public challenges were saved
     */
    private suspend fun fetchLocalChallenges(
        context: Context,
        location: GeoLocation
    ): ArrayList<PublicChallenge> {

        // getting the saved values from sharedPreferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val lat = sharedPreferences.getFloat(KEY_LAT, -1f)
        val lng = sharedPreferences.getFloat(KEY_LNG, -1f)
        val date = sharedPreferences.getString(KEY_DATE, null)
        if (date == null || lng == -1f || lat == -1f) return ArrayList()

        // checking the date difference
        val downloadDate = Constants.challengeDateFormat.parse(date)
        val currentDate = Date()
        val timeDifferenceInMinutes =
            (currentDate.time - (downloadDate?.time ?: 0)) / 1000 / 60
        Log.d(TAG, "Time difference from last fetch? $timeDifferenceInMinutes")
        if (timeDifferenceInMinutes > 60) return ArrayList()

        // checking the distance difference
        val downloadLocation = GeoLocation(lat.toDouble(), lng.toDouble())
        val distanceInMetres = GeoFireUtils.getDistanceBetween(location, downloadLocation)
        Log.d(TAG, "Distance to last fetch location: $distanceInMetres")
        if (distanceInMetres > 3000) return ArrayList()

        // the local data is fresh enough and the location was almost the same
        Log.d(TAG, "Local DB is ok, getting challenges")
        val dbHelperImpl =
            PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        return dbHelperImpl.getAll() as ArrayList<PublicChallenge>
    }


    suspend fun getChallengeFromRoom(id: String, context: Context): PublicChallenge? {
        val dbHelperImpl =
            PublicChallengeDbHelperImpl(PublicChallengeDbBuilder.getInstance(context))
        return dbHelperImpl.getChallenge(id)
    }

    companion object {
        const val PUBLIC_CHALLENGES_COLLECTION = "challenges"
        private const val ROUTES_COLLECTION = "routes"
        private const val GEOHASH_FIELD = "geohash"

        private const val TAG = "REPO"

        private const val KEY_LNG = "com.sziffer.challenger.lng"
        private const val KEY_LAT = "com.sziffer.challenger.lat"
        private const val KEY_DATE = "com.sziffer.challenger.date"

        private const val TOLERANCE_IN_METRES = 200.0
        private const val DISTANCE_TOLERANCE_PERCENT = 0.10

    }
}
