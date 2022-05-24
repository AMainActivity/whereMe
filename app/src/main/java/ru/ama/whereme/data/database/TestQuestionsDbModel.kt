package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.gson.annotations.SerializedName
import ru.ama.whereme.data.database.TestQuestionsDbModel.Companion.fieldOwnerId
import ru.ama.whereme.data.database.TestQuestionsDbModel.Companion.fieldTestId
import ru.ama.whereme.data.database.TestQuestionsDbModel.Companion.tabTestQuestions

@Entity(
  tableName = tabTestQuestions,
  foreignKeys = [
    ForeignKey(entity = TestInfoDbModel::class, parentColumns = [fieldTestId], childColumns = [fieldOwnerId],
      onDelete = ForeignKey.CASCADE)
  ]
)
data class TestQuestionsDbModel(
  val number: Int,
  val question: String,
  val imageUrl : String? = null,
  val answers: List<String>,
  val correct: List<Int>,
  val ownerTestId: Int
){
    @PrimaryKey(autoGenerate = true)
    var _id: Long = 0
	
	    companion object {
         const val tabTestQuestions = "test_questions"
         const val fieldTestId = "testId"
         const val fieldOwnerId = "ownerTestId"
		}
}
