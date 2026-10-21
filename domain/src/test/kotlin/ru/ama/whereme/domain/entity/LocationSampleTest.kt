package ru.ama.whereme.domain.entity

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocationSampleTest {
    private val now = 100_000_000_000L
    private val fix = LocationSample(0.0, 0.0, 50f, 0f, Instant.ofEpochMilli(1_700_000_000_000L), now)

    @Test fun zeroCoordinatesAreARealLocation() { assertTrue(fix.isUsable(now)) }
    @Test fun futureAndMissingTimestampsAreRejected() {
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
        assertFalse(fix.copy(time = Instant.EPOCH).isUsable(now))
    }
    @Test fun sampleDoesNotExpireWhileWaitingForBetterAccuracy() {
        assertTrue(fix.isUsable(now + 30_000_000_000L))
        assertTrue(fix.isUsable(now + 30_000_000_001L))
        assertTrue(fix.isUsable(now + 120_000_000_000L))
    }
}
