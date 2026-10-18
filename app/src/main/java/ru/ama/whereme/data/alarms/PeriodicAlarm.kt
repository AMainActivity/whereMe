package ru.ama.whereme.data.alarms

import dagger.hilt.android.AndroidEntryPoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import ru.ama.whereme.tracking.TrackingScheduler
import ru.ama.whereme.presentation.MyForegroundService
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicAlarm : BroadcastReceiver() {
    @Inject
    lateinit var repo: TrackingScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        if (!repo.isTimeToGetLocaton()) return
        try {
            // onStartCommand handles both an existing service and a new one without binding.
            ContextCompat.startForegroundService(context, MyForegroundService.newIntent(context))
        } catch (failure: IllegalStateException) {
            Log.w("PeriodicAlarm", "Service start restricted: " + failure.javaClass.simpleName)
        } catch (_: SecurityException) {
            Log.w("PeriodicAlarm", "Location access is unavailable")
        }
    }
}
