package ru.ama.whereme.data.workers

import android.app.ActivityManager
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import ru.ama.whereme.presentation.MyForegroundService
import java.text.SimpleDateFormat


class Alarm : BroadcastReceiver() {

    fun isTime1BolseTime2(time1: String, time2: String): Boolean {
        val sdf = SimpleDateFormat("HH:mm")
        val tim1 = sdf.parse(time1)
        val tim2 = sdf.parse(time2)
        return tim1.compareTo(tim2) > 0
    }

    fun isMyServiceRunning(ctx: Context, serviceClass: Class<*>): Boolean {
        val manager =
            ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onReceive(p0: Context?, p1: Intent?) {
        Log.e("onReceiveAlarm", "doAlarm")
        try {
            val b = false//isTime1BolseTime2(SimpleDateFormat("HH:mm").format(Date()), "18:00")
            Log.e("isTime1BolseTime3", b.toString())
            if (!b) {
                if (!isMyServiceRunning(p0!!.applicationContext, MyForegroundService::class.java)) {
                    ContextCompat.startForegroundService(
                        p0.applicationContext,
                        MyForegroundService.newIntent(p0.applicationContext)
                    )
                    Log.e("onStartCommand", "isMyServiceRunning")
                } else {
                    Log.e("onStartCommand2", "isMyServiceRunning")
                    p0.applicationContext.bindService(
                        MyForegroundService.newIntent(p0.applicationContext),
                        serviceConnection,
                        0
                    )

                }
            } else {
                if (isMyServiceRunning(p0!!.applicationContext, MyForegroundService::class.java))
                    p0.applicationContext.stopService(MyForegroundService.newIntent(p0!!.applicationContext))
            }


        } catch (e: Exception) {
            Log.d("doAlarm", "Exception getting location -->  ${e.message}")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = (service as? MyForegroundService.LocalBinder) ?: return
            val foregroundService = binder.getService()
            foregroundService.startGetLocations()
            Log.e("serviceConnection", "onServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }
}