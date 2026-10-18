package ru.ama.whereme.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RouteRequestTest {
    @Test
    fun parsesLegacyLatitudeLongitudeOrder() {
        val route = requireNotNull(RouteRequest.parse("10.5#20.5#30.5#40.5"))
        assertEquals(10.5, route.fromLatitude, 0.0)
        assertEquals(20.5, route.fromLongitude, 0.0)
        assertEquals(30.5, route.toLatitude, 0.0)
        assertEquals(40.5, route.toLongitude, 0.0)
    }

    @Test
    fun zeroAndCoordinateBoundariesAreValid() {
        assertNotNull(RouteRequest.parse("0#0#0#0"))
        assertNotNull(RouteRequest.parse("-90#-180#90#180"))
    }

    @Test
    fun rejectsNonFiniteValues() {
        for (value in listOf("NaN", "Infinity", "-Infinity", "1e309")) {
            assertNull(RouteRequest.parse("$value#0#0#0"))
        }
    }

    @Test
    fun rejectsCoordinatesOutsideTheirRanges() {
        for (payload in listOf("91#0#0#0", "0#181#0#0", "0#0#-91#0", "0#0#0#-181")) {
            assertNull(RouteRequest.parse(payload))
        }
    }

    @Test
    fun rejectsMissingOrAdditionalCoordinates() {
        for (payload in listOf("", "0#0#0", "0#0#0#0#0", "0#0##0", "#0#0#0")) {
            assertNull(RouteRequest.parse(payload))
        }
    }

    @Test
    fun rejectsScriptsUrlsAndOversizedInput() {
        for (payload in listOf("javascript:alert(1)#0#0#0", "https://example.com#0#0#0", "0".repeat(257))) {
            assertNull(RouteRequest.parse(payload))
        }
    }
}
