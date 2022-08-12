package ru.ama.whereme.data.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.ama.whereme.data.repository.WmRepositoryImpl
import javax.inject.Inject

class StartServiceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repo: WmRepositoryImpl
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.e("StartServiceReceiver","onReceive сработал")
        repo.runWorker(15)
    }
}