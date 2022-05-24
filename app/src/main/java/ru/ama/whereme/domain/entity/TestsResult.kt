package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TestsResult(
    val title: String,
    val timeForTest: String,
    val countOfAnswers: Int,
    val winner: Boolean,
    val countOfRightAnswers: Int,
    val countOfQuestions: Int,
    val testsSettings: TestsSettings,
    val answerOfTest: List<AnswerOfTest>
) : Parcelable {
	
companion object {

        private const val STO_PROCENTOV = 100
    }
	
    val percentageOfRightAnswers: Int
        get() = if (countOfQuestions > 0) {
            ((countOfRightAnswers / countOfQuestions.toDouble()) * STO_PROCENTOV).toInt()
        } else {
            0
        }
}
