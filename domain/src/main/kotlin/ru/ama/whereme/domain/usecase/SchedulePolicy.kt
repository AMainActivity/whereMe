package ru.ama.whereme.domain.usecase

import java.util.Calendar
import java.util.TimeZone
import ru.ama.whereme.domain.entity.SettingsDomModel

object SchedulePolicy {
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val NOON_HOUR = 12

    /** Monday is zero. An overnight interval belongs to the weekday on which it starts. */
    fun isActive(settings: SettingsDomModel, weekday: Int, minute: Int): Boolean {
        require(
            weekday in 0 until SettingsDomModel.DAYS_PER_WEEK && minute in 0 until MINUTES_PER_DAY
        )
        if (!settings.isEnable) return false
        val start = minutes(settings.start)
        val end = minutes(settings.end)
        if (start == end) return false
        return if (start < end)
            settings.days[weekday] == SettingsDomModel.DAY_ENABLED && minute in start until end
        else
            (minute >= start && settings.days[weekday] == SettingsDomModel.DAY_ENABLED) ||
                (minute < end &&
                    settings.days[
                            (weekday + SettingsDomModel.DAYS_PER_WEEK - 1) %
                                SettingsDomModel.DAYS_PER_WEEK] == SettingsDomModel.DAY_ENABLED)
    }

    fun nextStart(settings: SettingsDomModel, nowMillis: Long, zone: TimeZone): Long? {
        if (
            !settings.isEnable ||
                settings.start == settings.end ||
                settings.days.none { it == SettingsDomModel.DAY_ENABLED }
        )
            return null
        val today = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        for (offset in 0..SettingsDomModel.DAYS_PER_WEEK) {
            val candidate =
                (today.clone() as Calendar).apply {
                    // Move at noon, then set the requested wall time afresh for every date (DST).
                    set(Calendar.HOUR_OF_DAY, NOON_HOUR)
                    add(Calendar.DAY_OF_YEAR, offset)
                    set(Calendar.HOUR_OF_DAY, minutes(settings.start) / MINUTES_PER_HOUR)
                    set(Calendar.MINUTE, minutes(settings.start) % MINUTES_PER_HOUR)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            if (
                candidate.timeInMillis > nowMillis &&
                    settings.days[weekday(candidate)] == SettingsDomModel.DAY_ENABLED
            )
                return candidate.timeInMillis
        }
        return null
    }

    /** End of the active interval, using local calendar arithmetic across DST/time-zone changes. */
    fun activeUntil(settings: SettingsDomModel, nowMillis: Long, zone: TimeZone): Long? {
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val currentMinute = now[Calendar.HOUR_OF_DAY] * MINUTES_PER_HOUR + now[Calendar.MINUTE]
        if (!isActive(settings, weekday(now), currentMinute)) return null
        return (now.clone() as Calendar)
            .apply {
                if (
                    minutes(settings.start) > minutes(settings.end) &&
                        currentMinute >= minutes(settings.start)
                )
                    add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, minutes(settings.end) / MINUTES_PER_HOUR)
                set(Calendar.MINUTE, minutes(settings.end) % MINUTES_PER_HOUR)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            .timeInMillis
    }

    private fun weekday(calendar: Calendar) =
        (calendar[Calendar.DAY_OF_WEEK] - Calendar.MONDAY + SettingsDomModel.DAYS_PER_WEEK) %
            SettingsDomModel.DAYS_PER_WEEK

    private fun minutes(time: String) =
        time.substringBefore(':').toInt() * MINUTES_PER_HOUR + time.substringAfter(':').toInt()
}
