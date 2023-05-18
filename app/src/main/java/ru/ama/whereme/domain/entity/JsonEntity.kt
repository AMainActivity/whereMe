package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class JsonEntity(
    val error: Boolean,
    val message: String
) : Parcelable