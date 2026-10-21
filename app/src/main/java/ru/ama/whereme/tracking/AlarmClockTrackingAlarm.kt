package ru.ama.whereme.tracking

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import ru.ama.whereme.domain.tracking.RecordingSession
import ru.ama.whereme.domain.tracking.TrackingAlarm
import ru.ama.whereme.presentation.MyForegroundService
import ru.ama.whereme.presentation.SplashActivity

/** Sampling and the daily schedule use different PendingIntent identities. */
@Singleton
class AlarmClockTrackingAlarm @Inject constructor(private val application: Application) : TrackingAlarm {
    private val manager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(session: RecordingSession) {
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) { "Exact alarm access required" }
        val open = PendingIntent.getActivity(application, OPEN_APP_REQUEST_CODE,
            Intent(application, SplashActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(session.nextAtMillis, open),
            requireNotNull(pending(MyForegroundService.wakeIntent(application, session), PendingIntent.FLAG_UPDATE_CURRENT)))
        Log.d(TAG, "Next sampling alarm: ${session.nextAtMillis}")
    }

    override fun cancel() {
        pending(MyForegroundService.wakeIntent(application, null), PendingIntent.FLAG_NO_CREATE)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun pending(intent: Intent, flags: Int): PendingIntent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        PendingIntent.getForegroundService(application, SAMPLE_REQUEST_CODE, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    else PendingIntent.getService(application, SAMPLE_REQUEST_CODE, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    private companion object {
        const val OPEN_APP_REQUEST_CODE = 0
        const val SAMPLE_REQUEST_CODE = 2
        const val TAG = "TrackingAlarm"
    }
}
