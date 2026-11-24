package com.woosungtracking.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HikeDao {
    @Insert
    suspend fun insertHike(hike: Hike): Long

    @Update
    suspend fun updateHike(hike: Hike)

    @Query("SELECT * FROM hikes WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun observeFinishedHikes(): Flow<List<Hike>>

    @Query("SELECT * FROM hikes WHERE id = :id")
    fun observeHike(id: Long): Flow<Hike?>

    @Query("SELECT * FROM hikes WHERE id = :id")
    suspend fun getHike(id: Long): Hike?

    @Query("SELECT * FROM hikes WHERE endTime IS NULL")
    suspend fun getUnfinishedHikes(): List<Hike>

    @Insert
    suspend fun insertPoint(point: TrackPoint)

    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY time")
    suspend fun getPoints(hikeId: Long): List<TrackPoint>

    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    @Query("SELECT * FROM photos WHERE hikeId = :hikeId ORDER BY time")
    fun observePhotos(hikeId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE hikeId = :hikeId ORDER BY time")
    suspend fun getPhotos(hikeId: Long): List<Photo>

    @Query("DELETE FROM track_points WHERE hikeId = :hikeId")
    suspend fun deletePoints(hikeId: Long)

    @Query("DELETE FROM photos WHERE hikeId = :hikeId")
    suspend fun deletePhotos(hikeId: Long)

    @Query("DELETE FROM hikes WHERE id = :hikeId")
    suspend fun deleteHikeRow(hikeId: Long)

    @Transaction
    suspend fun deleteHike(hikeId: Long) {
        deletePoints(hikeId)
        deletePhotos(hikeId)
        deleteHikeRow(hikeId)
    }
}
