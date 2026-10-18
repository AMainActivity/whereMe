package ru.ama.whereme.tracking

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.usecase.SchedulePolicy
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingScheduler @Inject constructor(
    private val application: Application,
    private val settings: SettingsRepository
) {
    suspend fun getWorkingTime() = settings.settings.first()

    suspend fun isTimeToGetLocation(): Boolean {
        val now = Calendar.getInstance()
        return SchedulePolicy.isActive(getWorkingTime(), weekday(now), now[Calendar.HOUR_OF_DAY] * 60 + now[Calendar.MINUTE])
    }

    suspend fun runAlarmClock() {
        check(application.trackingCapabilities().requirement(TrackingAction.SCHEDULE) == TrackingRequirement.READY)
        settings.updateSettings { current ->
            require(current.start != current.end && current.days.any { it == "1" })
            current.copy(isEnable = true)
        }
        scheduleNextAlarmClockIfEnabled()
    }

    suspend fun scheduleNextAlarmClockIfEnabled() {
        val alarm = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent()
        alarm.cancel(pending)
        val value = getWorkingTime()
        if (!value.isEnable || value.start == value.end || value.days.none { it == "1" }) return
        val next = SchedulePolicy.nextStart(value, System.currentTimeMillis(), TimeZone.getDefault()) ?: return
        alarm.setAlarmClock(AlarmManager.AlarmClockInfo(next, pending), pending)
    }

    suspend fun cancelAlarmClock() {
        settings.updateSettings { it.copy(isEnable = false) }
        (application.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent())
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(application, 0,
        Intent(application, AlarmClockStart::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun weekday(calendar: Calendar) = (calendar[Calendar.DAY_OF_WEEK] + 5) % 7
}
