package ru.ama.whereme.domain.entity

import java.time.Instant

data class LocationDb(
    val _id: Long,
    // Legacy wire field: passed through unchanged; date arithmetic uses datestart/dateend.
    val datetime: String,
    val datestart: Instant,
    val dateend: Instant? = null,
    val info: String? = null,
    val latitude: Double,
    val longitude: Double,
    val sourceId: Int,
    val accuracy: Float,
    val velocity: Float,
    val isWrite: Int
)
