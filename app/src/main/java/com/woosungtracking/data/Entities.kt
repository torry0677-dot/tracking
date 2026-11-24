package com.woosungtracking.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "hikes")
data class Hike(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceM: Double = 0.0,
    val movingTimeMs: Long = 0,
    val elevationGainM: Double = 0.0,
    val maxAltitudeM: Double? = null,
    val minAltitudeM: Double? = null,
)

@Entity(tableName = "track_points", indices = [Index("hikeId")])
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hikeId: Long,
    val time: Long,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val speed: Float?,
    val accuracy: Float?,
    /** 일시정지 후 다시 시작한 첫 지점이면 true (앞 지점과의 거리를 더하지 않음) */
    val segmentStart: Boolean = false,
)

@Entity(tableName = "photos", indices = [Index("hikeId")])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hikeId: Long,
    val time: Long,
    val lat: Double?,
    val lng: Double?,
    val path: String,
)
