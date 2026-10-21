package ru.ama.whereme.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import ru.ama.whereme.domain.entity.SettingsDomModel

object SchedulePolicy {
    fun nextStart(settings: SettingsDomModel, now: Instant, zone: ZoneId): Instant? {
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0..SettingsDomModel.DAYS_PER_WEEK) {
            val interval = intervalOn(settings, today.plusDays(offset.toLong()), zone) ?: continue
            if (interval.start > now) return interval.start
        }
        return null
    }

    fun activeUntil(settings: SettingsDomModel, now: Instant, zone: ZoneId): Instant? {
        val today = now.atZone(zone).toLocalDate()
        // An overnight interval belongs to its start day, including across DST.
        return listOf(today.minusDays(1), today)
            .mapNotNull { intervalOn(settings, it, zone) }
            .filter { now >= it.start && now < it.end }
            .maxOfOrNull { it.end }
    }

    private data class Interval(val start: Instant, val end: Instant)

    /** Both alarm planning and activity checks use these same resolved boundaries. */
    private fun intervalOn(settings: SettingsDomModel, date: LocalDate, zone: ZoneId): Interval? {
        if (!settings.isEnable || settings.start == settings.end ||
            settings.days[date.dayOfWeek.value - 1] != SettingsDomModel.DAY_ENABLED) return null
        val endDate = if (settings.start > settings.end) date.plusDays(1) else date
        val start = atTime(date, settings.start, zone)
        val end = atTime(endDate, settings.end, zone)
        // A spring gap can move the start to/past the end: no interval or alarm that day.
        return if (start < end) Interval(start, end) else null
    }

    // Shift gaps forward and choose the later occurrence of an ambiguous wall time.
    private fun atTime(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.atTime(time).atZone(zone).withLaterOffsetAtOverlap().toInstant()
}
