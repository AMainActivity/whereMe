package ru.ama.whereme.domain.usecase

import ru.ama.whereme.domain.entity.SettingsDomModel
import java.util.Calendar
import java.util.TimeZone

object SchedulePolicy {
    /** Monday is zero. An overnight interval belongs to the weekday on which it starts. */
    fun isActive(settings: SettingsDomModel, weekday: Int, minute: Int): Boolean {
        require(weekday in 0..6 && minute in 0..1439)
        if (!settings.isEnable) return false
        val start = minutes(settings.start)
        val end = minutes(settings.end)
        if (start == end) return false
        return if (start < end) settings.days[weekday] == "1" && minute in start until end
        else (minute >= start && settings.days[weekday] == "1") ||
            (minute < end && settings.days[(weekday + 6) % 7] == "1")
    }

    fun nextStart(settings: SettingsDomModel, nowMillis: Long, zone: TimeZone): Long? {
        if (!settings.isEnable || settings.start == settings.end || settings.days.none { it == "1" }) return null
        val today = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        for (offset in 0..7) {
            val candidate = (today.clone() as Calendar).apply {
                // Move at noon, then set the requested wall time afresh for every date (DST).
                set(Calendar.HOUR_OF_DAY, 12)
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, minutes(settings.start) / 60)
                set(Calendar.MINUTE, minutes(settings.start) % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > nowMillis && settings.days[weekday(candidate)] == "1") return candidate.timeInMillis
        }
        return null
    }

    /** End of the active interval, using local calendar arithmetic across DST/time-zone changes. */
    fun activeUntil(settings: SettingsDomModel, nowMillis: Long, zone: TimeZone): Long? {
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val currentMinute = now[Calendar.HOUR_OF_DAY] * 60 + now[Calendar.MINUTE]
        if (!isActive(settings, weekday(now), currentMinute)) return null
        return (now.clone() as Calendar).apply {
            if (minutes(settings.start) > minutes(settings.end) && currentMinute >= minutes(settings.start)) add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, minutes(settings.end) / 60)
            set(Calendar.MINUTE, minutes(settings.end) % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun weekday(calendar: Calendar) = (calendar[Calendar.DAY_OF_WEEK] + 5) % 7
    private fun minutes(time: String) = time.substring(0, 2).toInt() * 60 + time.substring(3).toInt()
}
