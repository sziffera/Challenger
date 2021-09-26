package com.sziffer.challenger.model.challenge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sziffer.challenger.utils.Constants
import java.util.*

@Entity(tableName = Constants.Database.PUBLIC_CHALLENGES_TABLE_NAME)
data class PublicChallenge(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "geohash") val geoHash: String,
    @ColumnInfo(name = "distance") val distance: Double, // in m
    @ColumnInfo(name = "duration") val duration: Long, //in sec
    @ColumnInfo(name = "elevation_gained") val elevationGained: Int,
    @ColumnInfo(name = "type") val type: ChallengeType,
    @ColumnInfo(name = "timestamp") var timestamp: Date,
    @ColumnInfo(name = "route") var route: ArrayList<PublicRouteItem>?
)
