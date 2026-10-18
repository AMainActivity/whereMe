package ru.ama.whereme.presentation.map

/** Legacy web contract: fromLatitude#fromLongitude#toLatitude#toLongitude. */
data class RouteRequest private constructor(
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double
) {
    companion object {
        fun parse(payload: String): RouteRequest? {
            if (payload.length > 256) return null
            val parts = payload.split('#')
            if (parts.size != 4) return null
            val values = parts.map { it.toDoubleOrNull() ?: return null }
            if (values.any { !it.isFinite() }) return null
            if (values[0] !in -90.0..90.0 || values[2] !in -90.0..90.0 ||
                values[1] !in -180.0..180.0 || values[3] !in -180.0..180.0
            ) return null
            return RouteRequest(values[0], values[1], values[2], values[3])
        }
    }
}
