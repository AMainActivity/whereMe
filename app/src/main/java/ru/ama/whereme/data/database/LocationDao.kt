package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

@Query("SELECT * FROM tab_locations  ORDER BY _id asc ")
    fun getLocations2(): List<LocationDbModel>

@Query("SELECT * FROM tab_locations  ORDER BY _id asc ")
    fun getLocations(): LiveData<List<LocationDbModel>>

    @Query("SELECT * FROM tab_locations  ORDER BY _id desc limit 1 ")
    fun getLastValue(): LocationDbModel

    @Query("update tab_locations  set info =  :newInfo  where _id=:id")
    fun updateLocationById(id:Int, newInfo:String):Int

    @Query("update tab_locations  set dateend =  :newTime  where _id=:id")
    fun updateTime2ById(id:Int, newTime:Long):Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(mLoc: LocationDbModel)
	
	@Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationList(locList: List<LocationDbModel>):List<Long>
}