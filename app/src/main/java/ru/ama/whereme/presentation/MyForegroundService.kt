package ru.ama.whereme.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import ru.ama.whereme.R
import ru.ama.whereme.data.repository.WmRepositoryImpl
import ru.ama.whereme.domain.entity.DatasToJson
import ru.ama.whereme.domain.entity.SettingsDomModel
import java.util.*
import javax.inject.Inject

class MyForegroundService : LifecycleService() {

    //private val coroutineScope = CoroutineScope(Dispatchers.IO)
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
        val isGooglePlayServicesAvailab = lifecycleScope.async(Dispatchers.IO) {
            repo.isGooglePlayServicesAvailable()
        }
        lifecycleScope.launch(Dispatchers.Main) {
            startTimer()
            if (isGooglePlayServicesAvailab.await()) {
                //  lifecycleScope.launch(Dispatchers.Main) {
                repo.startLocationUpdates()
                //}
                //  lifecycleScope.launch {
                //      sd.await()
                //  }
                log(repo.isEnathAccuracy.value.toString() + "")


            } else
                Log.e("SERVICE_TAG3", "isGooglePlayServicesAvailable false")
        }

        repo.isEnathAccuracy.observe(this)
        {

        }

    }

    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val resultIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val resultPendingIntent: PendingIntent? = PendingIntent.getActivity(
            this, 0, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("служба")
            .setContentText("определения местоположения")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            .setContentIntent(resultPendingIntent)
        return b
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
                    lifecycleScope.launch(Dispatchers.IO) {
                        repo.saveLocation(repo.mBestLoc)
                    }
                sendData4Net()
               /* if (!repo.isCurTimeBetweenSettings())
                    stopSelf()
                else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        sendData4Net()
                        // Log.e("getLocations4Net", s)*/
                        repo.runAlarm(workingTimeModel.timeOfWorkingWM.toLong())
                  //  }
                    cancelTimer(
                        getString(R.string.app_name),
                        "не было найдено, скоро повтор " + repo.getDate(
                            Calendar.getInstance().getTime().time
                        )
                    )
               // }
            }
        }
        timer?.start()
    }

    /*
    val d = viewModelScope.async(Dispatchers.IO) {
                getAllQuestionsListUseCase(testId)

            }
            viewModelScope.launch {
                _listOfAnswers.postValue(d.await())
            }
    */
    private fun sendData4Net() {
        if (repo.isInternetConnected()) {
            var idList: MutableList<Long> = ArrayList()
            //   var json = JSONObject()
            val d = lifecycleScope.async(Dispatchers.IO) {
                val res = repo.getLocations4Net()
                //  Log.e("res", res.toString())
                for (i in res.indices) {
                    idList.add(res[i]._id)
                }
                /*     val sd = Gson().toJson(res)
                     //  Log.e("Gson",sd.toString())
                     // LocationDb::class.java
                      val type: Type = object : TypeToken<List<LocationDb?>?>() {}.type
                     // val inpList: List<LocationDb> = Gson().fromJson(sd, type)
                     json.put("tokenJWT", repo.getWmJwToken())
                     json.put("mdata",sd)
                     /* val requestBody: RequestBody = */RequestBody.create(
                     MediaType.parse("application/json"),
                     json.toString()
                 )*/
                val json1 = Gson().toJson(DatasToJson(repo.getWmJwToken(), res))
                Log.e("Gson", json1.toString())
                RequestBody.create(
                    MediaType.parse("application/json"), json1
                        .toString()
                )
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val sdsd = d.await()
                Log.e("idList", idList.size.toString())
                if (idList.size > 0) {
                    Log.e("Gson2", sdsd.toString())
                    val response = repo.writeLoc4Net(sdsd)
                    Log.e("responseCode", response.respCode.toString())
                    Log.e("response", response.toString())
                    if (response.respIsSuccess) {
                        response.mBody?.let {
                            if (it.error == false && it.message.length > 0) {
                                repo.updateIsWrite(idList)
                            }
                            reRunGetLocations()
                        }
                    } else {
                        try {
                            val jObjError = JSONObject(response.respError?.string())

                            Log.e(
                                "responseError",
                                jObjError.toString()/*.getJSONObject("error").getString("message")*/
                            )
                        } catch (e: Exception) {
                            Log.e("responseError", e.message.toString())
                        }
                        reRunGetLocations()
                    }
                } else
                    reRunGetLocations()
            }


        } else
            reRunGetLocations()
    }

    private fun reRunGetLocations() {
        if (!repo.isCurTimeBetweenSettings())
            stopSelf()
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

                sendData4Net()
                repo.runAlarm(workingTimeModel.timeOfWorkingWM.toLong())
                cancelTimer(
                    getString(R.string.app_name),
                    "успешно получено " + repo.getDate(Calendar.getInstance().getTime().time)
                )
                isEnath = true
            }
        }
        startGetLocations()


        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        isServiseAlive?.invoke(false)
        lifecycleScope.cancel()
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