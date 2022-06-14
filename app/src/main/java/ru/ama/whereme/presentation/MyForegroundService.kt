package ru.ama.whereme.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.TestsRepositoryImpl
import javax.inject.Inject

class MyForegroundService : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
  private val component by lazy {
        (application as MyApp).component
    }	
     var lld2 : LiveData<Location?>?=null
    
	@Inject
    lateinit var repo: TestsRepositoryImpl
	
    override fun onCreate() {
		component.inject(this)
        super.onCreate()
        log("onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
       /*coroutineScope.launch {
        lld2 = repo.getLocation2()
}*/   var lloc:Location?=null

        val sd=coroutineScope.async {
            lloc=repo.getLastLocation().value
            //Toast.makeText(applicationContext,lloc.toString(), Toast.LENGTH_SHORT).show()

            Log.d("SERVICE_TAG2", "MyForegroundService: ${lloc.toString()}")}
        coroutineScope.launch {
            sd.await()
            lloc = repo.getLocation2().value
        }
        Toast.makeText(applicationContext,lloc.toString(), Toast.LENGTH_SHORT).show()
        log(lld2?.value.toString())
		
		if(repo.isEnathAccuracy.value==true) stopSelf()
		
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
			coroutineScope.launch {
                repo.stopLocationUpdates()
		}
        coroutineScope.cancel()
        log("onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private fun log(message: String) {
        Log.e("SERVICE_TAG", "MyForegroundService: $message")
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("служба")
        .setContentText("опеределения местоположения")
        .setSmallIcon(R.drawable.ic_launcher_background)
        .build()

    companion object {

        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_ID = 1

        fun newIntent(context: Context): Intent {
            return Intent(context, MyForegroundService::class.java)
        }
    }
}