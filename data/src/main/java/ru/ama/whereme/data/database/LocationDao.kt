package ru.ama.whereme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM tab_locations WHERE ownerKey=:owner AND isWrite=0 ORDER BY _id ASC LIMIT :limit")
    suspend fun getPendingLocations(owner: String, limit: Int): List<LocationDbModel>

    @Query("UPDATE tab_locations SET isWrite=1 WHERE _id=:id AND ownerKey=:owner AND isWrite=0 " +
        "AND datetime=:datetime AND datestart=:datestart AND dateend IS :dateend " +
        "AND info IS :info AND latitude=:latitude AND longitude=:longitude " +
        "AND sourceId=:sourceId AND accuracy=:accuracy AND velocity=:velocity")
    suspend fun acknowledgeUnchanged(
        id: Long, owner: String, datetime: String, datestart: Long, dateend: Long?, info: String?,
        latitude: Double, longitude: Double, sourceId: Int, accuracy: Float, velocity: Float
    ): Int

    @Query("SELECT * FROM tab_locations WHERE datestart >= :start AND datestart < :end ORDER BY _id ASC")
    fun getLocations(start: Long, end: Long): Flow<List<LocationDbModel>>

    @Query("SELECT * FROM tab_locations WHERE _id=:id")
    suspend fun getLocation(id: Long): LocationDbModel?

    @Query("UPDATE tab_locations SET dateend=:newTime, info=:newInfo, isWrite=0 WHERE _id=:id")
    suspend fun updateInterval(id: Long, newTime: Long, newInfo: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocation(location: LocationDbModel): Long

    @Query("SELECT EXISTS(SELECT 1 FROM legacy_history WHERE id=1)")
    suspend fun hasLegacyHistory(): Boolean

    @Query("UPDATE tab_locations SET ownerKey=:owner WHERE _id <= (SELECT lastRowId FROM legacy_history WHERE id=1) AND ownerKey IS NULL")
    suspend fun assignLegacyHistory(owner: String?)

    @Query("DELETE FROM legacy_history WHERE id=1")
    suspend fun finishLegacyAssignment()
}
