package ru.ama.whereme.domain.entity

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class TestInfo(
    val testId: Int,
    val title: String,
    val mainImageUrl : String? = null,
    val minPercentOfRightAnswers: Int,
    val testTimeInSeconds: Int,
    val countOfQuestions: Int
) : Parcelable
