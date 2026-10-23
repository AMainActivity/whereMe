package ru.ama.whereme.feature.map

import org.junit.Assert.*
import org.junit.Test

class RouteRequestTest {
    @Test fun preservesLatitudeLongitudeOrder() {
        val route = requireNotNull(RouteRequest.create(10.5, 20.5, 30.5, 40.5))
        assertEquals(10.5, route.fromLatitude, 0.0)
        assertEquals(20.5, route.fromLongitude, 0.0)
        assertEquals(30.5, route.toLatitude, 0.0)
        assertEquals(40.5, route.toLongitude, 0.0)
    }
    @Test fun zeroAndCoordinateBoundariesAreValid() {
        assertNotNull(RouteRequest.create(0.0, 0.0, 0.0, 0.0))
        assertNotNull(RouteRequest.create(-90.0, -180.0, 90.0, 180.0))
    }
    @Test fun rejectsNonFiniteValuesInEitherEndpoint() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(RouteRequest.create(value, 0.0, 0.0, 0.0))
            assertNull(RouteRequest.create(0.0, value, 0.0, 0.0))
            assertNull(RouteRequest.create(0.0, 0.0, value, 0.0))
            assertNull(RouteRequest.create(0.0, 0.0, 0.0, value))
        }
    }
    @Test fun rejectsCoordinatesOutsideTheirRanges() {
        assertNull(RouteRequest.create(91.0, 0.0, 0.0, 0.0))
        assertNull(RouteRequest.create(0.0, 181.0, 0.0, 0.0))
        assertNull(RouteRequest.create(0.0, 0.0, -91.0, 0.0))
        assertNull(RouteRequest.create(0.0, 0.0, 0.0, -181.0))
    }
}
