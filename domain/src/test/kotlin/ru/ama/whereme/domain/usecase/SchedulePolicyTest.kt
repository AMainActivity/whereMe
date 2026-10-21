package ru.ama.whereme.domain.usecase

import java.time.Duration
import java.time.LocalDateTime
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.domain.entity.SettingsDomModel

class SchedulePolicyTest {
    private val monday = SettingsDomModel.DEFAULT.copy(days = listOf("1", "0", "0", "0", "0", "0", "0"), isEnable = true)
    @Test fun intervalIncludesStartAndExcludesEnd() {
        assertFalse(isActive(monday, DayOfWeek.of(1), LocalTime.ofSecondOfDay(539 * 60L)))
        assertTrue(isActive(monday, DayOfWeek.of(1), LocalTime.ofSecondOfDay(540 * 60L)))
        assertTrue(isActive(monday, DayOfWeek.of(1), LocalTime.ofSecondOfDay(1019 * 60L)))
        assertFalse(isActive(monday, DayOfWeek.of(1), LocalTime.ofSecondOfDay(1020 * 60L)))
    }
    @Test fun disabledScheduleAndExcludedDayNeverStartRecording() {
        assertFalse(isActive(monday.copy(isEnable = false), DayOfWeek.of(1), LocalTime.ofSecondOfDay(600 * 60L)))
        assertFalse(isActive(monday, DayOfWeek.of(2), LocalTime.ofSecondOfDay(600 * 60L)))
        assertFalse(isActive(monday.copy(days = List(7) { "0" }), DayOfWeek.of(1), LocalTime.ofSecondOfDay(600 * 60L)))
    }
    @Test fun overnightIntervalBelongsToItsStartDay() {
        val value = monday.copy(start = LocalTime.parse("22:00"), end = LocalTime.parse("06:00"))
        assertFalse(isActive(value, DayOfWeek.of(1), LocalTime.ofSecondOfDay(300 * 60L)))
        assertTrue(isActive(value, DayOfWeek.of(1), LocalTime.ofSecondOfDay(1320 * 60L)))
        assertTrue(isActive(value, DayOfWeek.of(2), LocalTime.ofSecondOfDay(359 * 60L)))
        assertFalse(isActive(value, DayOfWeek.of(2), LocalTime.ofSecondOfDay(360 * 60L)))
        assertFalse(isActive(value, DayOfWeek.of(2), LocalTime.ofSecondOfDay(1320 * 60L)))
    }
    @Test fun sundayNightContinuesOnMonday() {
        val value = monday.copy(days = listOf("0", "0", "0", "0", "0", "0", "1"), start = LocalTime.parse("23:00"), end = LocalTime.parse("01:00"))
        assertTrue(isActive(value, DayOfWeek.of(1), LocalTime.ofSecondOfDay(30 * 60L)))
        assertFalse(isActive(value, DayOfWeek.of(2), LocalTime.ofSecondOfDay(30 * 60L)))
    }
    @Test fun equalTimesDoNotMeanTwentyFourHourRecording() {
        assertFalse(isActive(monday.copy(start = LocalTime.parse("09:00"), end = LocalTime.parse("09:00")), DayOfWeek.of(1), LocalTime.ofSecondOfDay(540 * 60L)))
    }
    private fun isActive(settings: SettingsDomModel, weekday: DayOfWeek, time: LocalTime): Boolean {
        val date = LocalDate.of(2026, 10, 19).plusDays(weekday.value - 1L) // Monday.
        return SchedulePolicy.activeUntil(settings, date.atTime(time).toInstant(java.time.ZoneOffset.UTC),
            java.time.ZoneOffset.UTC) != null
    }

    private fun time(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    @Test fun nextStartSkipsExcludedWeekdaysAndNeverUsesPastTime() {
        val zone = ZoneId.of("Asia/Yekaterinburg")
        val now = time(zone, 2026, 10, 19, 10, 0)
        assertEquals(time(zone, 2026, 10, 26, 9, 0), SchedulePolicy.nextStart(monday, now, zone))
        assertNull(SchedulePolicy.nextStart(monday.copy(isEnable = false), now, zone))
        assertNull(SchedulePolicy.nextStart(monday.copy(days = List(7) { "0" }), now, zone))
    }
    @Test fun springClockChangeDoesNotShiftNextDaysRequestedTime() {
        val zone = ZoneId.of("America/New_York")
        val value = monday.copy(days = List(7) { "1" }, start = LocalTime.parse("02:30"))
        val now = time(zone, 2026, 3, 8, 4, 0)
        assertEquals(time(zone, 2026, 3, 9, 2, 30), SchedulePolicy.nextStart(value, now, zone))
    }
    @Test fun overnightEndUsesLocalDateInsteadOfFixedTwentyFourHours() {
        val zone = ZoneId.of("America/New_York")
        val value = monday.copy(days = List(7) { "1" }, start = LocalTime.parse("22:00"), end = LocalTime.parse("06:00"))
        val beforeJump = time(zone, 2026, 3, 7, 23, 0)
        val end = time(zone, 2026, 3, 8, 6, 0)
        assertEquals(end, SchedulePolicy.activeUntil(value, beforeJump, zone))
        assertEquals(6 * 60 * 60 * 1000L, Duration.between(beforeJump, end).toMillis())
    }
    @Test fun changingTimeZoneRecalculatesTheNextLocalStart() {
        val utc = ZoneId.of("UTC")
        val yekaterinburg = ZoneId.of("Asia/Yekaterinburg")
        val now = time(utc, 2026, 10, 19, 0, 0)
        assertEquals(time(utc, 2026, 10, 19, 9, 0), SchedulePolicy.nextStart(monday, now, utc))
        assertEquals(time(utc, 2026, 10, 19, 4, 0), SchedulePolicy.nextStart(monday, now, yekaterinburg))
    }

    @Test fun springGapMovesTheRequestedStartForward() {
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(2, 30))
        assertEquals(java.time.Instant.parse("2026-03-08T07:30:00Z"),
            SchedulePolicy.nextStart(settings, java.time.Instant.parse("2026-03-08T05:00:00Z"), ZoneId.of("America/New_York")))
    }

    @Test fun autumnOverlapUsesTheLaterOccurrenceForStartAndEnd() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(1, 30), end = LocalTime.of(1, 45))
        assertEquals(java.time.Instant.parse("2026-11-01T06:30:00Z"),
            SchedulePolicy.nextStart(settings, java.time.Instant.parse("2026-11-01T04:00:00Z"), zone))
        assertEquals(java.time.Instant.parse("2026-11-01T06:45:00Z"),
            SchedulePolicy.activeUntil(settings, java.time.Instant.parse("2026-11-01T06:35:00Z"), zone))
    }

    @Test fun autumnEndRemainsActiveThroughBothOccurrencesOfTheRepeatedHour() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.MIDNIGHT, end = LocalTime.of(1, 45))
        val end = java.time.Instant.parse("2026-11-01T06:45:00Z")
        for (now in listOf("2026-11-01T05:44:00Z", "2026-11-01T05:45:00Z",
                "2026-11-01T05:59:00Z", "2026-11-01T06:00:00Z", "2026-11-01T06:44:59Z")) {
            assertEquals(now, end, SchedulePolicy.activeUntil(settings, java.time.Instant.parse(now), zone))
        }
        assertNull(SchedulePolicy.activeUntil(settings, end, zone))
    }

    @Test fun autumnStartDoesNotActivateAtTheFirstOccurrence() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(1, 30), end = LocalTime.of(2, 0))
        val first = java.time.Instant.parse("2026-11-01T05:35:00Z")
        val start = java.time.Instant.parse("2026-11-01T06:30:00Z")
        assertNull(SchedulePolicy.activeUntil(settings, first, zone))
        assertEquals(start, SchedulePolicy.nextStart(settings, first, zone))
        assertEquals(java.time.Instant.parse("2026-11-01T07:00:00Z"),
            SchedulePolicy.activeUntil(settings, start, zone))
    }

    @Test fun springEndRemainsActiveUntilItsShiftedBoundary() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(1, 0), end = LocalTime.of(2, 30))
        val end = java.time.Instant.parse("2026-03-08T07:30:00Z")
        for (now in listOf("2026-03-08T06:59:00Z", "2026-03-08T07:00:00Z", "2026-03-08T07:29:59Z")) {
            assertEquals(now, end, SchedulePolicy.activeUntil(settings, java.time.Instant.parse(now), zone))
        }
        assertNull(SchedulePolicy.activeUntil(settings, end, zone))
    }

    @Test fun springStartUsesTheSameBoundaryForAlarmAndActivity() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(2, 30), end = LocalTime.of(4, 0))
        val beforeStart = java.time.Instant.parse("2026-03-08T07:10:00Z")
        val start = java.time.Instant.parse("2026-03-08T07:30:00Z")
        assertNull(SchedulePolicy.activeUntil(settings, beforeStart, zone))
        assertEquals(start, SchedulePolicy.nextStart(settings, beforeStart, zone))
        assertEquals(java.time.Instant.parse("2026-03-08T08:00:00Z"),
            SchedulePolicy.activeUntil(settings, start, zone))
    }

    @Test fun springGapWithNoRemainingIntervalSkipsTheDaysAlarm() {
        val zone = ZoneId.of("America/New_York")
        val before = java.time.Instant.parse("2026-03-08T05:00:00Z")
        for (end in listOf(LocalTime.of(3, 15), LocalTime.of(3, 30))) {
            val settings = monday.copy(days = List(7) { "1" }, start = LocalTime.of(2, 30), end = end)
            assertEquals(java.time.Instant.parse("2026-03-09T06:30:00Z"),
                SchedulePolicy.nextStart(settings, before, zone))
            assertNull(SchedulePolicy.activeUntil(settings, java.time.Instant.parse("2026-03-08T07:30:00Z"), zone))
        }
    }

    @Test fun overnightAutumnIntervalStillBelongsToTheEnabledSaturday() {
        val zone = ZoneId.of("America/New_York")
        val settings = monday.copy(days = listOf("0", "0", "0", "0", "0", "1", "0"),
            start = LocalTime.of(22, 0), end = LocalTime.of(1, 45))
        assertEquals(java.time.Instant.parse("2026-11-01T06:45:00Z"),
            SchedulePolicy.activeUntil(settings, java.time.Instant.parse("2026-11-01T05:50:00Z"), zone))
        assertNull(SchedulePolicy.activeUntil(settings, java.time.Instant.parse("2026-11-02T05:30:00Z"), zone))
    }

}
