package ru.ama.whereme.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import ru.ama.whereme.domain.entity.SettingsDomModel

class SchedulePolicyTest {
    private val monday = SettingsDomModel.DEFAULT.copy(days = listOf("1", "0", "0", "0", "0", "0", "0"), isEnable = true)
    @Test fun intervalIncludesStartAndExcludesEnd() {
        assertFalse(SchedulePolicy.isActive(monday, 0, 539))
        assertTrue(SchedulePolicy.isActive(monday, 0, 540))
        assertTrue(SchedulePolicy.isActive(monday, 0, 1019))
        assertFalse(SchedulePolicy.isActive(monday, 0, 1020))
    }
    @Test fun disabledScheduleAndExcludedDayNeverStartRecording() {
        assertFalse(SchedulePolicy.isActive(monday.copy(isEnable = false), 0, 600))
        assertFalse(SchedulePolicy.isActive(monday, 1, 600))
        assertFalse(SchedulePolicy.isActive(monday.copy(days = List(7) { "0" }), 0, 600))
    }
    @Test fun overnightIntervalBelongsToItsStartDay() {
        val value = monday.copy(start = "22:00", end = "06:00")
        assertFalse(SchedulePolicy.isActive(value, 0, 300))
        assertTrue(SchedulePolicy.isActive(value, 0, 1320))
        assertTrue(SchedulePolicy.isActive(value, 1, 359))
        assertFalse(SchedulePolicy.isActive(value, 1, 360))
        assertFalse(SchedulePolicy.isActive(value, 1, 1320))
    }
    @Test fun sundayNightContinuesOnMonday() {
        val value = monday.copy(days = listOf("0", "0", "0", "0", "0", "0", "1"), start = "23:00", end = "01:00")
        assertTrue(SchedulePolicy.isActive(value, 0, 30))
        assertFalse(SchedulePolicy.isActive(value, 1, 30))
    }
    @Test fun equalTimesDoNotMeanTwentyFourHourRecording() {
        assertFalse(SchedulePolicy.isActive(monday.copy(start = "09:00", end = "09:00"), 0, 540))
    }
    private fun time(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun nextStartSkipsExcludedWeekdaysAndNeverUsesPastTime() {
        val zone = TimeZone.getTimeZone("Asia/Yekaterinburg")
        val now = time(zone, 2026, 10, 19, 10, 0)
        assertEquals(time(zone, 2026, 10, 26, 9, 0), SchedulePolicy.nextStart(monday, now, zone))
        assertNull(SchedulePolicy.nextStart(monday.copy(isEnable = false), now, zone))
        assertNull(SchedulePolicy.nextStart(monday.copy(days = List(7) { "0" }), now, zone))
    }
    @Test fun springClockChangeDoesNotShiftNextDaysRequestedTime() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val value = monday.copy(days = List(7) { "1" }, start = "02:30")
        val now = time(zone, 2026, 3, 8, 4, 0)
        assertEquals(time(zone, 2026, 3, 9, 2, 30), SchedulePolicy.nextStart(value, now, zone))
    }
    @Test fun overnightEndUsesLocalDateInsteadOfFixedTwentyFourHours() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val value = monday.copy(days = List(7) { "1" }, start = "22:00", end = "06:00")
        val beforeJump = time(zone, 2026, 3, 7, 23, 0)
        val end = time(zone, 2026, 3, 8, 6, 0)
        assertEquals(end, SchedulePolicy.activeUntil(value, beforeJump, zone))
        assertEquals(6 * 60 * 60 * 1000L, end - beforeJump)
    }
    @Test fun changingTimeZoneRecalculatesTheNextLocalStart() {
        val utc = TimeZone.getTimeZone("UTC")
        val yekaterinburg = TimeZone.getTimeZone("Asia/Yekaterinburg")
        val now = time(utc, 2026, 10, 19, 0, 0)
        assertEquals(time(utc, 2026, 10, 19, 9, 0), SchedulePolicy.nextStart(monday, now, utc))
        assertEquals(time(utc, 2026, 10, 19, 4, 0), SchedulePolicy.nextStart(monday, now, yekaterinburg))
    }

}
