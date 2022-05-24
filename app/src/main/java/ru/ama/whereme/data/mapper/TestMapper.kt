package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.TestInfoDbModel
import ru.ama.whereme.data.database.TestQuestionsDbModel
import ru.ama.whereme.domain.entity.TestInfo
import ru.ama.whereme.domain.entity.TestQuestion
import javax.inject.Inject

class TestMapper @Inject constructor() {



    fun mapDbModelToEntity(dbModel: TestQuestionsDbModel) = TestQuestion(
        number = dbModel.number,
        question = dbModel.question,
        imageUrl  = dbModel.imageUrl,
        answers = dbModel.answers,
        correct =  dbModel.correct
    )

    fun mapDataDbModelToEntity(dbModel: TestInfoDbModel) = TestInfo(
        testId=dbModel.testId,
        title=dbModel.title,
        mainImageUrl=dbModel.mainImageUrl,
        minPercentOfRightAnswers=dbModel.minPercentOfRightAnswers,
        testTimeInSeconds=dbModel.testTimeInSeconds,
        countOfQuestions=dbModel.countOfQuestions
    )

    companion object {
        const val BASE_IMAGE_URL = "https://kol.hhos.ru/test/tests/img/"
    }
}
