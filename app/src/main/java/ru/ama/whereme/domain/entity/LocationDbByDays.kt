package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.OffsetDateTime

@Parcelize
data class LocationDbByDays(
    val _id: Long,
    val datestart: String,
    val dateend: String?=null
) : Parcelable