package ru.ama.whereme.feature.map

import ru.ama.whereme.domain.entity.LocationSample

/** Validated native coordinates for an external 2GIS route. */
@ConsistentCopyVisibility
data class RouteRequest
private constructor(
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
) {
    companion object {
        fun create(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): RouteRequest? {
            val values = listOf(fromLatitude, fromLongitude, toLatitude, toLongitude)
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
