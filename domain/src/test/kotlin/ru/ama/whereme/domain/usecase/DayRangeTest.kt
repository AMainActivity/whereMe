package ru.ama.whereme.domain.usecase

import java.time.Duration
import java.time.ZoneId
import java.time.LocalDate
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DayRangeTest {
    @Test fun utcDayHasExclusiveNextMidnight() {
        val day = DayRange.of(LocalDate.parse("2026-10-19"), ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-10-19T00:00:00Z"), day.start)
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), day.end)
    }
    @Test fun daylightSavingDaysAreCalendarDaysNotFixedDurations() {
        val zone = ZoneId.of("Europe/Berlin")
        val spring = DayRange.of(LocalDate.parse("2026-03-29"), zone)
        val autumn = DayRange.of(LocalDate.parse("2026-10-25"), zone)
        assertEquals(23 * 3_600_000L, Duration.between(spring.start, spring.end).toMillis())
        assertEquals(25 * 3_600_000L, Duration.between(autumn.start, autumn.end).toMillis())
    }
    @Test fun sampleDayDoesNotDependOnTodaysDate() {
        val zone = ZoneId.of("Asia/Yekaterinburg")
        assertEquals(DayRange.of(LocalDate.parse("2026-10-20"), zone),
            DayRange.containing(Instant.parse("2026-10-19T20:00:00Z"), zone))
    }
    @Test fun invalidCalendarDateIsRejected() {
        try { DayRange.of(LocalDate.parse("2026-02-31")); fail("Invalid day") } catch (_: java.time.format.DateTimeParseException) { }
    }
}
