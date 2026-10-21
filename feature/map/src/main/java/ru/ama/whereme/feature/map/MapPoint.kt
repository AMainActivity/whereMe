package ru.ama.whereme.feature.map

import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
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
        private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.ROOT)
        private fun format(time: Instant): String =
            dateTimeFormat.withZone(ZoneId.systemDefault()).format(time)

        fun from(location: LocationDb) =
            MapPoint(
                location._id,
                location.datetime,
                format(location.datestart),
                location.dateend?.let(::format),
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
