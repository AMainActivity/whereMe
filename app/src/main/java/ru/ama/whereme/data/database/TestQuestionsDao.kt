package ru.ama.whereme.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TestQuestionsDao {
    @Query("SELECT * FROM test_questions where number in (select number from test_questions order by random() limit 20) ORDER BY number asc")
    fun getQuestionList(): LiveData<List<TestQuestionsDbModel>>
	
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionList(priceList: List<TestQuestionsDbModel>):List<Long>

    @Query("SELECT * FROM test_questions where ownerTestId=:testId and number in (select number from test_questions  where ownerTestId=:testId order by random() limit :limit) ORDER BY number asc")
    fun getQuestionListByTestId(testId:Int,limit:Int): List<TestQuestionsDbModel>
}

