package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocationDb(
    val _id: Long,
    val datetime: String,
    val datestart: String,
    val dateend: String? = null,
    val info: String? = null,
    val latitude: Double,
    val longitude: Double,
    val sourceId: Int,
    val accuracy: Float,
    val velocity: Float,
    val isWrite: Int
) : Parcelable