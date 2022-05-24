package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {

@Query("SELECT * FROM tab_locations  ORDER BY _id asc ")
    fun getLocations(): List<LocationDbModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(mLoc: LocationDbModel)
	
	@Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationList(locList: List<LocationDbModel>):List<Long>
}