package ru.ama.whereme.domain.usecase

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Local calendar day, including 23/25-hour DST days. SQL compares indexed epoch timestamps. */
data class DayRange(val start: Long, val end: Long) {
    companion object {
        const val DATE_PATTERN = "dd.MM.yyyy"
        fun parse(date: String, zone: TimeZone = TimeZone.getDefault()): DayRange {
            val format = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply { timeZone = zone; isLenient = false }
            val value = requireNotNull(format.parse(date))
            require(format.format(value) == date)
            return containing(value.time, zone)
        }
        fun containing(timeMillis: Long, zone: TimeZone = TimeZone.getDefault()): DayRange {
            val calendar = Calendar.getInstance(zone).apply {
                this.timeInMillis = timeMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return DayRange(start, calendar.timeInMillis)
        }
    }
}
