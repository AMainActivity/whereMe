package ru.ama.whereme.data.network.model

import kotlinx.serialization.Serializable
import ru.ama.whereme.domain.entity.LocationDb

/** Existing JSON contract shared by the backend and the map page. */
@Serializable
data class LocationDto(
    val _id: Long,
    val datetime: String,
    val datestart: String,
    val dateend: String?,
    val info: String?,
    val latitude: Double,
    val longitude: Double,
    val sourceId: Int,
    val accuracy: Float,
    val velocity: Float,
    val isWrite: Int
) {
    companion object {
        fun from(location: LocationDb) = LocationDto(
            location._id, location.datetime, location.datestart, location.dateend, location.info,
            location.latitude, location.longitude, location.sourceId, location.accuracy,
            location.velocity, location.isWrite
        )
    }
}
