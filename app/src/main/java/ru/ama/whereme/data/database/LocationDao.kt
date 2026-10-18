package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

    @Query(
        "SELECT _id,datetime,datestart,dateend,info," +
                "latitude + cast(substr(cast(:param as text), 1,1)||\".\"||cast(:param as text) as double) as latitude," +
                "longitude + cast(substr(cast(:param+1 as text), 1,1)||\".\"||cast(:param+1 as text) as double) as longitude," +
                "sourceId,accuracy,velocity,isWrite FROM tab_locations  where isWrite=0 ORDER BY _id asc "
    )
    suspend fun getLocations4Net(param: Int): List<LocationDbModel>

    @Query("SELECT * FROM tab_locations WHERE isWrite=0 ORDER BY _id ASC")
    suspend fun getPendingLocations(): List<LocationDbModel>

    @Query("UPDATE tab_locations SET isWrite=1 WHERE _id=:id AND isWrite=0 " +
        "AND datetime=:datetime AND datestart=:datestart AND dateend IS :dateend " +
        "AND info IS :info AND latitude=:latitude AND longitude=:longitude " +
        "AND sourceId=:sourceId AND accuracy=:accuracy AND velocity=:velocity")
    suspend fun acknowledgeUnchanged(
        id: Long, datetime: String, datestart: Long, dateend: Long?, info: String?,
        latitude: Double, longitude: Double, sourceId: Int, accuracy: Float, velocity: Float
    ): Int

    @Query("SELECT * FROM tab_locations  ORDER BY _id asc ")
    fun getLocations(): LiveData<List<LocationDbModel>>

    @Query("SELECT * FROM tab_locations where strftime('%d.%m.%Y', datestart / 1000, 'unixepoch', 'localtime') =:mDate  ORDER BY _id asc ")
    fun getLocationsById(mDate: String): LiveData<List<LocationDbModel>>

    @Query("SELECT _id,datestart,dateend FROM tab_locations GROUP BY strftime('%d.%m.%Y', datestart / 1000, 'unixepoch', 'localtime') ORDER BY _id asc ")
    suspend fun getLocationsByDays(): List<LocationDbModelByDays>

    @Query("SELECT * FROM tab_locations where strftime('%d.%m.%Y', datestart / 1000, 'unixepoch', 'localtime') =:mDate ORDER BY _id desc limit 1 ")
    suspend fun getLastValue(mDate: String): LocationDbModel?

    @Query(
        "SELECT _id,strftime('%d.%m.%Y %H:%M:%S', datestart / 1000, 'unixepoch', 'localtime') as datetime,datestart,dateend," +
                "strftime('%d.%m.%Y %H:%M:%S', datestart / 1000, 'unixepoch') as info,latitude,longitude,sourceId,accuracy,velocity,isWrite FROM tab_locations ORDER BY _id desc limit 1 "
    )
    fun getLastValu1e(): List<LocationDbModel>

    @Query("UPDATE tab_locations SET dateend=:newTime, info=:newInfo, isWrite=0 WHERE _id=:id")
    suspend fun updateInterval(id: Long, newTime: Long, newInfo: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocation(mLoc: LocationDbModel): Long

}