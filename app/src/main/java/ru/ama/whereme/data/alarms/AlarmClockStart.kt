package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.presentation.MyForegroundService
import javax.inject.Inject

@AndroidEntryPoint
class AlarmClockStart : BroadcastReceiver() {
    @Inject
    lateinit var repo: WmRepositoryImpl

    override fun onReceive(context: Context, intent: Intent?) {
        if (!repo.getWorkingTime().isEnable) return
        // An excluded weekday must not terminate the daily schedule chain.
        repo.scheduleNextAlarmClockIfEnabled()
        if (!repo.isTimeToGetLocaton()) return
        try {
            ContextCompat.startForegroundService(context, MyForegroundService.newIntent(context))
        } catch (failure: IllegalStateException) {
            Log.w("AlarmClockStart", "Service start restricted: " + failure.javaClass.simpleName)
        } catch (_: SecurityException) {
            Log.w("AlarmClockStart", "Location access is unavailable")
        }
    }
}
