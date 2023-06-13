package ru.ama.whereme.data.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.presentation.MyApp
import javax.inject.Inject

class AfterBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repo: WmRepositoryImpl

    override fun onReceive(context: Context?, intent: Intent?) {
        val component =
            (context?.applicationContext as MyApp).component
        component.inject(this)
        Log.e("StartServiceReceiver", "onReceive сработал")
        repo.runAlarm(TIME_INTERVAL)
    }
    companion object {
        const val TIME_INTERVAL = 15L
    }
}