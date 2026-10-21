package ru.ama.whereme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.data.database.LocationDbModel.Companion.UPLOAD_PENDING
import ru.ama.whereme.data.database.LocationDbModel.Companion.UPLOAD_SENT

@Dao
interface LocationDao {
    @Query("SELECT MAX(COALESCE(dateend, datestart)) FROM tab_locations")
    fun observeLastRecordedTime(): Flow<Long?>

    @Query("SELECT * FROM tab_locations WHERE ownerKey=:owner AND isWrite=$UPLOAD_PENDING ORDER BY _id ASC LIMIT :limit")
    suspend fun getPendingLocations(owner: String, limit: Int): List<LocationDbModel>

    @Query("UPDATE tab_locations SET isWrite=$UPLOAD_SENT WHERE _id=:id AND ownerKey=:owner AND isWrite=$UPLOAD_PENDING " +
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

    @Query("UPDATE tab_locations SET dateend=:newTime, info=:newInfo, isWrite=$UPLOAD_PENDING WHERE _id=:id")
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
