package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

    @Query("SELECT * FROM tab_locations  where isWrite=0 ORDER BY _id asc ")
    suspend fun getLocations4Net(): List<LocationDbModel>	
	
    @Query("update tab_locations  set isWrite =  1  where _id in (:idList)")
    fun updateQuery(idList: List<Long>)

    @Query("SELECT * FROM tab_locations  ORDER BY _id asc ")
    fun getLocations(): LiveData<List<LocationDbModel>>

    @Query("SELECT * FROM tab_locations where strftime('%d.%m.%Y', datestart / 1000, 'unixepoch') =:mDate  ORDER BY _id asc ")
    fun getLocationsById(mDate: String): LiveData<List<LocationDbModel>>


    //CAST(strftime('%Y', datetime(date/1000, 'unixepoch')) AS int) AS year
    @Query("SELECT _id,datestart,dateend FROM tab_locations GROUP BY strftime('%d.%m.%Y', datestart / 1000, 'unixepoch') ORDER BY _id asc ")
    suspend fun getLocationsByDays(): List<LocationDbModelByDays>

    @Query("SELECT * FROM tab_locations where strftime('%d.%m.%Y', datestart / 1000, 'unixepoch') =:mDate ORDER BY _id desc limit 1 ")
    fun getLastValue(mDate: String): LocationDbModel

    @Query("update tab_locations  set info =  :newInfo,isWrite =  0 where _id=:id")
    fun updateLocationById(id: Int, newInfo: String): Int

    @Query("update tab_locations  set dateend =  :newTime  where _id=:id")
    fun updateTime2ById(id: Int, newTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(mLoc: LocationDbModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationList(locList: List<LocationDbModel>): List<Long>
}