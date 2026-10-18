package ru.ama.whereme.domain.entity

import org.junit.Assert.*
import org.junit.Test

class LocationSampleTest {
    private val now = 100_000_000_000L
    private val fix = LocationSample(0.0, 0.0, 50f, 0f, 1_700_000_000_000L, now)

    @Test fun zeroCoordinatesAreARealLocation() { assertTrue(fix.isUsable(now)) }
    @Test fun oldAndFutureFixesAreRejected() {
        assertFalse(fix.copy(elapsedRealtimeNanos = now - 30_000_000_001L).isUsable(now))
        assertFalse(fix.copy(elapsedRealtimeNanos = now + 1).isUsable(now))
        assertFalse(fix.copy(elapsedRealtimeNanos = 0).isUsable(now))
    }
    @Test fun invalidNumbersCannotReachDatabase() {
        assertFalse(fix.copy(latitude = Double.NaN).isUsable(now))
        assertFalse(fix.copy(longitude = 181.0).isUsable(now))
        assertFalse(fix.copy(latitude = -91.0).isUsable(now))
        assertFalse(fix.copy(accuracyMeters = Float.POSITIVE_INFINITY).isUsable(now))
        assertFalse(fix.copy(accuracyMeters = -1f).isUsable(now))
        assertFalse(fix.copy(speedMetersPerSecond = Float.NaN).isUsable(now))
        assertFalse(fix.copy(timeMillis = 0).isUsable(now))
    }
    @Test fun sampleExpiresWhileWaitingForBetterAccuracy() {
        assertTrue(fix.isUsable(now + 30_000_000_000L))
        assertFalse(fix.isUsable(now + 30_000_000_001L))
    }
}
