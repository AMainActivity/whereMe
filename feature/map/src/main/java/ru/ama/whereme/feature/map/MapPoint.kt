package ru.ama.whereme.feature.map

import kotlinx.serialization.Serializable
import ru.ama.whereme.domain.entity.LocationDb

/** JSON contract of the existing server-hosted map page. */
@Serializable
data class MapPoint(
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
    val isWrite: Int,
) {
    companion object {
        fun from(location: LocationDb) =
            MapPoint(
                location._id,
                location.datetime,
                location.datestart,
                location.dateend,
                location.info,
                location.latitude,
                location.longitude,
                location.sourceId,
                location.accuracy,
                location.velocity,
                location.isWrite,
            )
    }
}
