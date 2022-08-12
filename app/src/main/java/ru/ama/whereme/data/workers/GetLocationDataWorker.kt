package ru.ama.whereme.data.workers

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.presentation.MyForegroundService
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GetLocationDataWorker(
    context: Context,
    workerParameters: WorkerParameters,
    private val locationDao: LocationDao,
    // private val apiService: ApiService,
    private val mapper: WmMapper
) : Worker(context, workerParameters) {

    override fun onStopped() {
        super.onStopped()

        Log.e("serviceConnection","unbindService")
        applicationContext.unbindService(serviceConnection)
    }
    /*

    fun Context.isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == serviceClass.name }
    }
    */
    fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
private  val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = (service as? MyForegroundService.LocalBinder) ?: return
        val foregroundService = binder.getService()
        foregroundService.startGetLocations()
        Log.e("serviceConnection","onServiceConnected")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
    }
}

    override fun doWork(): Result {
        val ctx: Context = applicationContext
        /*while (true) {
            try {
                val topCoins = apiService.getTopCoinsInfo(limit = 50)
                val fSyms = mapper.mapNamesListToString(topCoins)
                val jsonContainer = apiService.getFullPriceList(fSyms = fSyms)
                val coinInfoDtoList = mapper.mapJsonContainerToListCoinInfo(jsonContainer)
                val dbModelList = coinInfoDtoList.map { mapper.mapDtoToDbModel(it) }
                coinInfoDao.insertPriceList(dbModelList)
            } catch (e: Exception) {
            }
            delay(10000)
        }*/
        try {
            /*
             ContextCompat.startForegroundService(
               this,
               MyForegroundService.newIntent(this)
           )
            */


            /*  val intent1 = Intent(context, task_service::class.java)
              intent1.setAction("startService")
              val jWorkTime= Gson().fromJson(
                  settings.getInstance(applicationContext).worktime,
                  adrrResponse.WorkTime::class.java
              )*/
            val b = false//isTime1BolseTime2(SimpleDateFormat("HH:mm").format(Date()), "18:00")
             Log.e("isTime1BolseTime3",b.toString())
            if (!b) {
                if (!isMyServiceRunning(MyForegroundService::class.java)) {
                    ContextCompat.startForegroundService(
                        ctx,
                        MyForegroundService.newIntent(ctx)
                    )
                    Log.e("onStartCommand", "isMyServiceRunning")
                }
                else
                {
                    Log.e("onStartCommand2", "isMyServiceRunning")
                    ctx.bindService(
                        MyForegroundService.newIntent(ctx),
                        serviceConnection,
                        0
                    )

                }
                /*
                 val workManager = WorkManager.getInstance(application)
    workManager.enqueueUniqueWork(
        RefreshDataWorker.NAME,
        ExistingWorkPolicy.REPLACE,
        RefreshDataWorker.makeRequest()
    )
                */

                /*  val request = OneTimeWorkRequestBuilder<MyWorker>().setInitialDelay(set.intervalOfWorker,TimeUnit.MINUTES).addTag("LocationWork").build()
                  WorkManager.getInstance(context).enqueueUniqueWork("LocationWork",ExistingWorkPolicy.KEEP,request )*/
            } else {
                //set.isGetLocation=false
                WorkManager.getInstance(ctx).cancelAllWorkByTag(GetLocationDataWorker.NAME)
                if (isMyServiceRunning(MyForegroundService::class.java))
                    ctx.stopService(MyForegroundService.newIntent(ctx))
            }


          return  Result.success()
        } catch (e: Exception) {
            Log.d("doWork", "Exception getting location -->  ${e.message}")
            val workManager = WorkManager.getInstance(ctx)
            workManager.enqueueUniqueWork(
                GetLocationDataWorker.NAME,
                ExistingWorkPolicy.REPLACE,
                GetLocationDataWorker.makeRequest(120)
            )
            return Result.failure()
        }

    }


    //проверка время1 больше время2
    fun isTime1BolseTime2(time1: String, time2: String): Boolean {
        val sdf = SimpleDateFormat("HH:mm")
        val tim1 = sdf.parse(time1)
        val tim2 = sdf.parse(time2)
        return tim1.compareTo(tim2) > 0
    }

    companion object {

        const val NAME = "RefreshDataWorker"

        fun makeRequest(timeInterval: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<GetLocationDataWorker>().setInitialDelay(
                timeInterval,
                TimeUnit.SECONDS
            ).addTag(NAME).build()
        }
    }

    class Factory @Inject constructor(
        private val locationDao: LocationDao,

        private val mapper: WmMapper
    ) : ChildWorkerFactory {

        override fun create(
            context: Context,
            workerParameters: WorkerParameters
        ): ListenableWorker {
            return GetLocationDataWorker(
                context,
                workerParameters,
                locationDao,
                mapper
            )
        }
    }
}
