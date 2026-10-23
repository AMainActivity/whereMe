package ru.ama.whereme.feature.map

import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import kotlinx.serialization.Serializable
import ru.ama.whereme.domain.entity.LocationDb

/** Local map payload. Date fields retain their existing display format; the API is unchanged. */
@Serializable
data class MapPoint(
    val _id: Long,
    val datestart: String,
    val dateend: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
) {
    companion object {
        private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.ROOT)
        private fun format(time: Instant): String =
            dateTimeFormat.withZone(ZoneId.systemDefault()).format(time)

        fun from(location: LocationDb) =
            MapPoint(
                location._id,
                format(location.datestart),
                location.dateend?.let(::format),
                location.latitude,
                location.longitude,
                location.accuracy,
            )
    }
}
