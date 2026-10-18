package ru.ama.whereme.tracking

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.data.alarms.PeriodicAlarm
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingScheduler @Inject constructor(
    private val application: Application,
    private val settings: SettingsRepository
) {
    fun getWorkingTime() = settings.getWorkingTime()
    private fun setWorkingTime(value: SettingsDomModel) = settings.setWorkingTime(value)

    private fun compare2Times(start: String, end: String): Boolean {
        var res = false
        val sdf = SimpleDateFormat("HH:mm")
        val strDate = sdf.parse(start) as Date
        val endDate = sdf.parse(end) as Date
        if (endDate.time >= strDate.time) {
            res = true
        }
        Log.e("compare2Times", "$strDate ### $endDate %%% $res")
        return res
    }

    fun isCurTimeBetweenSettings(): Boolean {
        val wTime = getWorkingTime()
        return (compare2Times(wTime.start, getCurrentTime()) && compare2Times(
            getCurrentTime(),
            wTime.end
        ))
    }
    fun isTimeToGetLocaton(): Boolean {
        var result = false
        val wTime = getWorkingTime()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        when (calendar[Calendar.DAY_OF_WEEK]) {
            Calendar.MONDAY -> {
                result = wTime.days[0] == DEFAULT_SETTINGS_DAY
            }

            Calendar.TUESDAY -> {
                result = wTime.days[1] == DEFAULT_SETTINGS_DAY
            }

            Calendar.WEDNESDAY -> {
                result = wTime.days[2] == DEFAULT_SETTINGS_DAY
            }

            Calendar.THURSDAY -> {
                result = wTime.days[3] == DEFAULT_SETTINGS_DAY
            }

            Calendar.FRIDAY -> {
                result = wTime.days[4] == DEFAULT_SETTINGS_DAY
            }

            Calendar.SATURDAY -> {
                result = wTime.days[5] == DEFAULT_SETTINGS_DAY
            }

            Calendar.SUNDAY -> {
                result = wTime.days[6] == DEFAULT_SETTINGS_DAY
            }
        }
        result = result && isCurTimeBetweenSettings()
        Log.e("IsTimeToGetLocaton", result.toString())
        return result
    }

    fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
    fun runAlarmClock() {
        setWorkingTime(getWorkingTime().copy(isEnable = true))
        scheduleNextAlarmClockIfEnabled()
    }

    fun scheduleNextAlarmClockIfEnabled() {
        val wTime = getWorkingTime()
        if (!wTime.isEnable) return
        val am = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(application, AlarmClockStart::class.java)
        val pi = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, wTime.start.split(SPLIT_DELIMITER)[0].toInt())
            set(Calendar.MINUTE, wTime.start.split(SPLIT_DELIMITER)[1].toInt())
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH) + 1)
        }
        am.cancel(pi)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                calendar.timeInMillis,
                pi
            ),
            pi
        )
        /* am.setRepeating(
             AlarmManager.RTC_WAKEUP,
             calendar.timeInMillis,
             DEFAULT_INTERVAL_ALARM,
             pi
         )*/
    }

    fun cancelAlarmClock() {
        Log.e("runAlarmClock", "cancelAlarmClock")
        val intent = Intent(application, AlarmClockStart::class.java)
        val sender = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
        setWorkingTime(getWorkingTime().copy(isEnable = false))
    }

    fun runAlarm(timeInterval: Long) {
        Log.e("runAlarm", "" + timeInterval)
        val am = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(application, PeriodicAlarm::class.java)
        val pi = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmTimeAtUTC = System.currentTimeMillis() + timeInterval * ONE_SECOND
        am.cancel(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeAtUTC, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmClockInfo: AlarmManager.AlarmClockInfo =
                AlarmManager.AlarmClockInfo(alarmTimeAtUTC, pi)
            am.setAlarmClock(alarmClockInfo, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        } else {
            am.set(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        }
    }

    fun cancelAlarm() {
        Log.e("runAlarm", "cancelAlarm")
        val intent = Intent(application, PeriodicAlarm::class.java)
        val sender = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
    }
    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm")
        return formatter.format(System.currentTimeMillis())
    }
    private companion object {
        const val DEFAULT_SETTINGS_DAY = "1"
        const val SPLIT_DELIMITER = ":"
        const val ONE_SECOND = 1000L
        const val EMPTY_INT = 0
    }
}
