package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ama.whereme.data.database.TestInfoDbModel.Companion.tabTestInfo

@Entity(tableName = tabTestInfo)
data class TestInfoDbModel(
  @PrimaryKey
  val testId: Int,
  val title: String,
  val mainImageUrl : String? = null,
  val minPercentOfRightAnswers: Int,
  val testTimeInSeconds: Int,
  val countOfQuestions: Int
)
{
	
	
	    companion object {
        const val tabTestInfo = "test_info"
		}
}

/*
class TestAndQuestions {
   @Embedded
   var test: TestInfoDbModel? = null
   @Relation(parentColumn = “testId”,
             entityColumn = “ownerTestId”)
   var questions: List<TestQuestionsDbModel> = ArrayList()
}
*/