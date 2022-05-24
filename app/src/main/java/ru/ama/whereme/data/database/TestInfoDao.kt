package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TestInfoDao {

@Query("SELECT * FROM test_info  ORDER BY testId asc ")
    fun getTestInfo(): List<TestInfoDbModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestInfo(testInfo: TestInfoDbModel)
	
	@Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestList(testList: List<TestInfoDbModel>):List<Long>
}