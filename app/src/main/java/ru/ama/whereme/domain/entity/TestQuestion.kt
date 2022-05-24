package ru.ama.whereme.domain.entity

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class TestQuestion
(
    val number: Int,
    val question: String,
    val imageUrl : String? = null,
    val answers: List<String>,
    val correct: List<Int>
): Parcelable
