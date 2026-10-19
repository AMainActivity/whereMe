package ru.ama.whereme.domain.usecase

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class DayRangeTest {
    @Test fun utcDayHasExclusiveNextMidnight() {
        val day = DayRange.parse("19.10.2026", TimeZone.getTimeZone("UTC"))
        assertEquals(Instant.parse("2026-10-19T00:00:00Z").toEpochMilli(), day.start)
        assertEquals(Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(), day.end)
    }
    @Test fun daylightSavingDaysAreCalendarDaysNotFixedDurations() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val spring = DayRange.parse("29.03.2026", zone)
        val autumn = DayRange.parse("25.10.2026", zone)
        assertEquals(23 * 3_600_000L, spring.end - spring.start)
        assertEquals(25 * 3_600_000L, autumn.end - autumn.start)
    }
    @Test fun sampleDayDoesNotDependOnTodaysDate() {
        val zone = TimeZone.getTimeZone("Asia/Yekaterinburg")
        assertEquals(DayRange.parse("20.10.2026", zone),
            DayRange.containing(Instant.parse("2026-10-19T20:00:00Z").toEpochMilli(), zone))
    }
    @Test fun invalidCalendarDateIsRejected() {
        try { DayRange.parse("31.02.2026"); fail("Invalid day") } catch (_: java.text.ParseException) { }
    }
}
