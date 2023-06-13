package ru.ama.whereme.data.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.presentation.MyApp
import ru.ama.whereme.presentation.MyForegroundService
import javax.inject.Inject


class AlarmClockStart : BroadcastReceiver() {
    @Inject
    lateinit var repo: WmRepositoryImpl
    override fun onReceive(ctx: Context?, intent: Intent?) {
        Log.e("onReceiveAlarmClock", "doAlarm")
        val component =
            (ctx?.applicationContext as MyApp).component
        component.inject(this)
        if (!repo.isMyServiceRunning(MyForegroundService::class.java)) {
            if (repo.isTimeToGetLocaton())
                ContextCompat.startForegroundService(
                    ctx.applicationContext,
                    MyForegroundService.newIntent(ctx.applicationContext)
                )
            /*else
               Toast.makeText(ctx.applicationContext, "isTimeToGetLocaton", Toast.LENGTH_SHORT)
                    .show()*/
            Log.e("onStartFromSet", "isMyServiceRunning")
        }
    }
}