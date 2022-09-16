package ru.ama.whereme.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.domain.entity.SettingsDomModel
import java.util.*
import javax.inject.Inject

class MyForegroundService : LifecycleService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var timer: CountDownTimer? = null

    private lateinit var workingTimeModel: SettingsDomModel
    private var isEnath = false
    var isServiseAlive: ((Boolean) -> Unit)? = null
    private val component by lazy {
        (application as MyApp).component
    }
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Inject
    lateinit var repo: WmRepositoryImpl

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        log("onBind")
        return LocalBinder()
    }

    inner class LocalBinder : Binder() {

        fun getService() = this@MyForegroundService
    }

    private val notificationBuilder by lazy {
        createNotificationBuilder()
    }

    fun startGetLocations() {
        workingTimeModel = repo.getWorkingTime()
        isEnath = false
        val isGooglePlayServicesAvailab = coroutineScope.async {
            repo.isGooglePlayServicesAvailable()
        }

        coroutineScope.launch {
            startTimer()
            if (isGooglePlayServicesAvailab.await()) {
                val sd = coroutineScope.async {
                    repo.startLocationUpdates()
                }
                coroutineScope.launch {
                    sd.await()
                }
                log(repo.isEnathAccuracy.value.toString() + "")


            } else
                Log.e("SERVICE_TAG3", "isGooglePlayServicesAvailable false")
        }

        repo.isEnathAccuracy.observe(this)
        {

        }

    }

    private fun createNotificationBuilder():NotificationCompat.Builder {
         val resultIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
         val resultPendingIntent: PendingIntent? =  PendingIntent.getActivity(
            this, 0, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b= NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("служба")
            .setContentText("определения местоположения")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            .setContentIntent(resultPendingIntent)
        return  b
    }

    private fun getFormattedLeftTime(millisUntilFinished: Long): String {

        val seconds = (millisUntilFinished / MILLIS_IN_SECONDS % SECONDS_IN_MINUTE).toInt()
        val minutes = millisUntilFinished / MILLIS_IN_SECONDS / SECONDS_IN_MINUTE
        return String.format(FORMATTED_STRING_MINUTE_SECOND, minutes, seconds)
    }

    private fun startTimer() {
        timer = object : CountDownTimer(
            workingTimeModel.timeOfWaitAccuracy.toLong() * 1000,
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val notification = notificationBuilder
                    .setContentText("")
                    .setContentTitle(
                        "служба: ${
                            if (millisUntilFinished < 1000L) "скоро повтор" else getFormattedLeftTime(
                                millisUntilFinished
                            )
                        }"
                    )
                    .setProgress(
                        workingTimeModel.timeOfWaitAccuracy,
                        (millisUntilFinished / MILLIS_IN_SECONDS).toInt(),
                        false
                    )
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            override fun onFinish() {
                        repo.stopLocationUpdates()
                if (repo.mBestLoc.longitude != 0.0)
                    coroutineScope.launch {
                        repo.saveLocation(repo.mBestLoc)
                    }
					if (!repo.isCurTimeBetweenSettings())
					stopSelf()
				else
					
                {
                    coroutineScope.launch {
                        repo.runAlarm(workingTimeModel.timeOfWorkingWM.toLong())
                    }
                    cancelTimer(
                        getString(R.string.app_name),
                        "не было найдено, скоро повтор " + repo.getDate(
                            Calendar.getInstance().getTime().time
                        )
                    )
                }
            }
        }
        timer?.start()
    }

    private fun cancelTimer(title: String, txtBody: String) {
        timer?.cancel()
        val notification = notificationBuilder
            .setContentTitle(title)
            .setContentText(txtBody)
            .setProgress(0, 0, false)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
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

        isServiseAlive?.invoke(true)
        repo.onLocationChangedListener = {
            Log.e("onLocationListener", "$it / $isEnath")
            if (it) {
                repo.stopLocationUpdates()
				if (!repo.isCurTimeBetweenSettings())
					stopSelf()
				else
				{
                repo.runAlarm(workingTimeModel.timeOfWorkingWM.toLong())
                cancelTimer(
                    getString(R.string.app_name),
                    "успешно получено " + repo.getDate(Calendar.getInstance().getTime().time)
                )
                isEnath = true
				}

            }
        }
        startGetLocations()


        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        isServiseAlive?.invoke(false)
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