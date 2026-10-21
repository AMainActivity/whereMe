package ru.ama.whereme.feature.map

import ru.ama.whereme.domain.entity.LocationSample

/** Legacy web contract: fromLatitude#fromLongitude#toLatitude#toLongitude. */
@ConsistentCopyVisibility
data class RouteRequest
private constructor(
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
) {
    companion object {
        private const val MAX_PAYLOAD_LENGTH = 256
        private const val COORDINATE_COUNT = 4

        fun parse(payload: String): RouteRequest? {
            if (payload.length > MAX_PAYLOAD_LENGTH) return null
            val parts = payload.split('#')
            if (parts.size != COORDINATE_COUNT) return null
            val values = parts.map { it.toDoubleOrNull() ?: return null }
            val (fromLatitude, fromLongitude, toLatitude, toLongitude) = values
            if (values.any { !it.isFinite() }) return null
            if (
                fromLatitude !in LocationSample.LATITUDE_RANGE ||
                    toLatitude !in LocationSample.LATITUDE_RANGE ||
                    fromLongitude !in LocationSample.LONGITUDE_RANGE ||
                    toLongitude !in LocationSample.LONGITUDE_RANGE
            )
                return null
            return RouteRequest(fromLatitude, fromLongitude, toLatitude, toLongitude)
        }
    }
}
