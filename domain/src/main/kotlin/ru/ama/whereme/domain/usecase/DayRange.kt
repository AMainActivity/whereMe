package ru.ama.whereme.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local calendar day, including 23/25-hour DST days; end is exclusive. */
data class DayRange(val start: Instant, val end: Instant) {
    companion object {
        fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = DayRange(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )

        fun containing(time: Instant, zone: ZoneId = ZoneId.systemDefault()) =
            of(time.atZone(zone).toLocalDate(), zone)
    }
}
