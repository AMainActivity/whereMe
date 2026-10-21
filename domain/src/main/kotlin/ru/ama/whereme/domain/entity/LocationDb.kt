package ru.ama.whereme.domain.entity


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
) {
    companion object {
        // Existing map/server contract; unlike UI dates, it is not locale-dependent.
        const val DATE_TIME_PATTERN = "dd.MM.yyyy HH:mm:ss"
    }
}
