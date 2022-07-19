package ru.ama.whereme.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.WmRepositoryImpl
import javax.inject.Inject

class MyForegroundService : LifecycleService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    //private lateinit var notificationManager:NotificationManager
    private var timer: CountDownTimer? = null
    private val component by lazy {
        (application as MyApp).component
    }	
     var lld2 : LiveData<Location?>?=null
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
	@Inject
    lateinit var repo: WmRepositoryImpl

   /* private lateinit var viewModel: ServiceViewModel
    @Inject
    lateinit var viewModelFactory: ViewModelFactory*/

    private val notificationBuilder by lazy {
        createNotificationBuilder()
    }
    private fun createNotificationBuilder() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("служба")
        .setContentText("определения местоположения")
        .setSmallIcon(R.drawable.ic_launcher_background)
     //   .setProgress(100, 0, false)
        .setOnlyAlertOnce(true)
    private fun getFormattedLeftTime(millisUntilFinished: Long): String {

        val seconds = (millisUntilFinished / MILLIS_IN_SECONDS % SECONDS_IN_MINUTE).toInt()
        val minutes = millisUntilFinished / MILLIS_IN_SECONDS / SECONDS_IN_MINUTE
        return String.format(FORMATTED_STRING_MINUTE_SECOND, minutes, seconds)
    }
 private fun startTimer() {
        timer = object : CountDownTimer(
            180 * 1000,
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val notification = notificationBuilder
                    .setContentTitle("служба: ${if (millisUntilFinished<1000L) "скоро повтор" else getFormattedLeftTime(millisUntilFinished)}")
                    .setProgress(180, (millisUntilFinished / MILLIS_IN_SECONDS).toInt(), false)
                    .build()
               // Log.e("millisUntilFinished",""+millisUntilFinished)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            override fun onFinish() {
             coroutineScope.launch {
                    repo.stopLocationUpdates()
                    repo.runWorker(300)
                }
                stopSelf()
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
        startTimer()
      //  viewModel = ViewModelProvider(application, viewModelFactory)[ServiceViewModel::class.java]

		repo.onLocationChangedListener = {
            it //=  result: LocationResult
        }

        repo.updateStartTime(SystemClock.elapsedRealtime())
       /*coroutineScope.launch {
        lld2 = repo.getLocation2()
}*/   var lloc:Location?=null

        val sd=coroutineScope.async {
          //  lloc=repo.getLastLocation().value
            repo.startLocationUpdates()
            //Toast.makeText(applicationContext,lloc.toString(), Toast.LENGTH_SHORT).show()

            Log.e("SERVICE_TAG2", "MyForegroundService: ${lloc.toString()}")}
        coroutineScope.launch {
            sd.await()
          //  lloc = repo.getLocation2().value
        }
        Toast.makeText(applicationContext,lloc.toString(), Toast.LENGTH_SHORT).show()
        log(lld2?.value.toString())
        log(repo.isEnathAccuracy.value.toString() +"")
        repo.isEnathAccuracy.observe(this)
        {
            if(it) {
               // updateMainNotify("1","2")
                coroutineScope.launch {
                    repo.stopLocationUpdates()
                    repo.runWorker(300)
                }
                stopSelf()
            }
        }
repo.kolvoPopytok.observe(this){
    //updateMainNotify("число попыток:", it)
}

		
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
		//	coroutineScope.launch {
              //  repo.stopLocationUpdates()
		//}
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
   /* private fun createNotificationChannel() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }*/


	//fun updateMainNotify(tit:String,mes:String)
    //{
        /*val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel("101", "channel", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(notificationChannel)
        }*/
    //    notificationManager.notify(NOTIFICATION_ID, createNotification(tit,mes))
  //  }

   /* private fun createNotification(tit:String,mes:String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(tit)
        .setContentText(mes)
        .setSmallIcon(R.drawable.ic_launcher_background)
        .build()*/

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