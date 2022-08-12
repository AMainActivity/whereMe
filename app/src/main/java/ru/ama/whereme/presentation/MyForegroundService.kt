package ru.ama.whereme.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.WmRepositoryImpl
import javax.inject.Inject

class MyForegroundService : LifecycleService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var timer: CountDownTimer? = null
    private var settingsWorkerReplayTime =50
    private val component by lazy {
        (application as MyApp).component
    }
    var lld2: LiveData<Location?>? = null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Inject
    lateinit var repo: WmRepositoryImpl

    override fun onBind(intent: Intent): IBinder? {
        log("onBind")
        return LocalBinder()
    }
    inner class LocalBinder : Binder() {

        fun getService() = this@MyForegroundService
    }
    private val notificationBuilder by lazy {
        createNotificationBuilder()
    }

    fun startGetLocations()
    {

        val isGooglePlayServicesAvailab = coroutineScope.async {
            repo.isGooglePlayServicesAvailable()
        }

        coroutineScope.launch {
            settingsWorkerReplayTime=repo.dsWorkerReplayTime
            startTimer()
            if (isGooglePlayServicesAvailab.await()) {
                val sd = coroutineScope.async {
                    repo.startLocationUpdates()
                    //   Log.e("SERVICE_TAG2", "MyForegroundService: ${lloc.toString()}")
                }
                coroutineScope.launch {
                    sd.await()
                }
                //  Toast.makeText(applicationContext,lloc.toString(), Toast.LENGTH_SHORT).show()
                log(repo.isEnathAccuracy.value.toString() + "")


            } else
                Log.e("SERVICE_TAG3", "isGooglePlayServicesAvailable false")
        }
        repo.isEnathAccuracy.observe(this)
        {
            if (it) {
                coroutineScope.launch {
                    repo.stopLocationUpdates()
                    repo.runWorker(settingsWorkerReplayTime.toLong())
                }
                timer?.cancel()
             //   stopSelf()
            }
        }

    }

    private fun createNotificationBuilder() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("служба")
        .setContentText("определения местоположения")
        .setSmallIcon(R.drawable.ic_launcher_background)
        .setOnlyAlertOnce(true)

    private fun getFormattedLeftTime(millisUntilFinished: Long): String {

        val seconds = (millisUntilFinished / MILLIS_IN_SECONDS % SECONDS_IN_MINUTE).toInt()
        val minutes = millisUntilFinished / MILLIS_IN_SECONDS / SECONDS_IN_MINUTE
        return String.format(FORMATTED_STRING_MINUTE_SECOND, minutes, seconds)
    }

    private fun startTimer() {
        timer = object : CountDownTimer(
            settingsWorkerReplayTime.toLong() * 1000,
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val notification = notificationBuilder
                    .setContentTitle(
                        "служба: ${
                            if (millisUntilFinished < 1000L) "скоро повтор" else getFormattedLeftTime(
                                millisUntilFinished
                            )
                        }"
                    )
                    .setProgress(settingsWorkerReplayTime, (millisUntilFinished / MILLIS_IN_SECONDS).toInt(), false)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            override fun onFinish() {
                if (repo.mBestLoc.longitude != 0.0)
                    coroutineScope.launch {
                        repo.saveLocation(repo.mBestLoc)
                    }
                else {
                    coroutineScope.launch {
                        repo.stopLocationUpdates()
                        repo.runWorker(settingsWorkerReplayTime.toLong())
                    }
                    timer?.cancel()
                  //  stopSelf()
                }
            }
        }
        timer?.start()
    }

    override fun onCreate() {
        component.inject(this)
        super.onCreate()
        log("onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log("onStartCommand")
      //  startTimer()

        repo.onLocationChangedListener = {
            it
        }
        startGetLocations()

/*

lifecycleScope.launch {
                repo.setLocationTurnedOn(false)
            }
			
			   if (repo.isLocationTurnedOn.first()) {}
			
*/



        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        coroutineScope.cancel()
        log("onDestroy")
    }


    private fun log(message: String) {
        Log.e("SERVICE_TAG", "MyForegroundService: $message")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }


    companion object {

        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_NAME = "channel_name"
        private const val NOTIFICATION_ID = 1
        private const val MILLIS_IN_SECONDS = 1000L
        private const val SECONDS_IN_MINUTE = 60
        private const val FORMATTED_STRING_MINUTE_SECOND = "%02d:%02d"

        fun newIntent(context: Context): Intent {
            return Intent(context, MyForegroundService::class.java)
        }
    }
}