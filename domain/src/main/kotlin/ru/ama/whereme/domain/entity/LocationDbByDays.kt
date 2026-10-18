package ru.ama.whereme.domain.entity


data class LocationDbByDays(
    val _id: Long,
    val datestart: String,
    val dateend: String? = null
)