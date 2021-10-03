package com.sziffer.challenger.model.challenge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sziffer.challenger.utils.Constants
import java.util.*

@Entity(tableName = Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME)
data class PublicChallenge(
    @PrimaryKey val id: String = "",
    @ColumnInfo(name = "lat") val lat: Double = 0.0,
    @ColumnInfo(name = "lng") val lng: Double = 0.0,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "geohash") val geoHash: String = "",
    @ColumnInfo(name = "distance") val distance: Double = 0.0, // in m
    @ColumnInfo(name = "duration") val duration: Long = 0, //in sec
    @ColumnInfo(name = "elevation_gained") val elevationGained: Int = 0,
    @ColumnInfo(name = "type") val type: ChallengeType = ChallengeType.CYCLING,
    @ColumnInfo(name = "timestamp") var timestamp: Date = Date(),
    @ColumnInfo(name = "route") var route: ArrayList<PublicRouteItem>? = null
)
