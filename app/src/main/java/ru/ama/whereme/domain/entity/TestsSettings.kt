package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TestsSettings(
    val minPercentOfRightAnswers: Int,
    val testTimeInSeconds: Int
) : Parcelable