package ru.ama.whereme.data.repository

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ama.whereme.data.database.*
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.mapper.WmMapperByDays
import ru.ama.whereme.data.mapper.WmMapperSettings
import ru.ama.whereme.data.workers.Alarm
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.LocationDbByDays
import ru.ama.whereme.domain.repository.WmRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class WmRepositoryImpl @Inject constructor(
    private val mapper: WmMapper,
    private val mapperByDays: WmMapperByDays,
    private val mapperSetTime: WmMapperSettings,
    private val locationDao: LocationDao,
    private val application: Application,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val googleApiAvailability: GoogleApiAvailability,
    private val mSettings: SharedPreferences
) : WmRepository {


    private lateinit var workingTimeModel: SettingsDomModel
    var mBestLoc = Location("bestLocationOfBadAccuracy")
    var onLocationChangedListener: ((Boolean) -> Unit)? = null

    private val callback = Callback()

    suspend fun isGooglePlayServicesAvailable(): Boolean = withContext(Dispatchers.Default) {
        when (googleApiAvailability.isGooglePlayServicesAvailable(application)) {
            ConnectionResult.SUCCESS -> true
            else -> false
        }
    }


    private val _isEnathAccuracy = MutableLiveData<Boolean>()
    val isEnathAccuracy: LiveData<Boolean>
        get() = _isEnathAccuracy

    private fun compare2Times(start: String, end: String): Boolean {
        var res = false
        val sdf = SimpleDateFormat("HH:mm")
        val strDate = sdf.parse(start)
        val endDate = sdf.parse(end)
        if (endDate.time > strDate.time) {
            res = true
        }
        Log.e("compare2Times", "$strDate ### $endDate %%% $res")
        return res
    }


    override fun IsTimeToGetLocaton(): Boolean {
        var result = false
        val wTime = getWorkingTime()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        when (calendar[Calendar.DAY_OF_WEEK]) {
            Calendar.MONDAY -> {
                result = wTime.days[0].equals("1")
            }
            Calendar.TUESDAY -> {
                result = wTime.days[1].equals("1")
            }
            Calendar.WEDNESDAY -> {
                result = wTime.days[2].equals("1")
            }
            Calendar.THURSDAY -> {
                result = wTime.days[3].equals("1")
            }
            Calendar.FRIDAY -> {
                result = wTime.days[4].equals("1")
            }
            Calendar.SATURDAY -> {
                result = wTime.days[5].equals("1")
            }
            Calendar.SUNDAY -> {
                result = wTime.days[6].equals("1")
            }
        }

        result= result && (compare2Times(wTime.start,getCurrentTime()) && compare2Times(getCurrentTime(),wTime.end))
    Log.e("IsTimeToGetLocaton",result.toString())
    return  result
    }

    override fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }


    override fun isInternetConnected(): Boolean {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork
            if (n != null) {
                val nc = cm.getNetworkCapabilities(n)
                return nc!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || nc.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            }
            return false
        } else {
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnectedOrConnecting
        }
    }

    override fun runAlarm(timeInterval: Long) {

        Log.e("runAlarm", "" + timeInterval)
        val am = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(application, Alarm::class.java)
        val pi = PendingIntent.getBroadcast(application, 0, i, 0)
        val alarmTimeAtUTC = System.currentTimeMillis() + timeInterval * 1_000L
        am.cancel(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeAtUTC, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmClockInfo: AlarmManager.AlarmClockInfo =
                AlarmManager.AlarmClockInfo(alarmTimeAtUTC, pi)
            am.setAlarmClock(alarmClockInfo, pi)
        }//KITKAT 19 OR ABOVE
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        }
        //FOR BELOW KITKAT ALL DEVICES
        else {
            am.set(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        }
    }

    override fun cancelAlarm() {
        Log.e("runAlarm", "cancelAlarm")
        val intent = Intent(application, Alarm::class.java)
        val sender = PendingIntent.getBroadcast(application, 0, intent, 0)
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
    }


    override suspend fun getGropingDays(): List<LocationDbByDays> {
        return locationDao.getLocationsByDays().map {
            mapperByDays.mapDbModelToEntity(it)
        }
    }


    override fun getWorkingTime(): SettingsDomModel {
        return mapperSetTime.mapDataModelToDomain(
            Gson().fromJson(
                worktime,
                SettingsDataModel::class.java
            )
        )
    }


    override fun setWorkingTime(dm: SettingsDomModel) {
        worktime = Gson().toJson(mapperSetTime.mapDomainToDataModel(dm))
    }


    override suspend fun getLocationById(mDate: String): LiveData<List<LocationDb>> {
        Log.e("getLocationById", mDate)
        return Transformations.map(locationDao.getLocationsById(mDate)) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }

    override suspend fun GetLocationsFromBd(): LiveData<List<LocationDb>> {
        return Transformations.map(locationDao.getLocations()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }

    override suspend fun loadData(): List<Int> {
        var listOfItems: MutableList<Int> = mutableListOf<Int>()

        return listOfItems


    }


    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        workingTimeModel = getWorkingTime()
        mBestLoc.latitude = 0.0
        mBestLoc.longitude = 0.0
        mBestLoc.accuracy = 1500f
        mBestLoc.speed = 0f
        mBestLoc.time = 0
        _isEnathAccuracy.value = false
        onLocationChangedListener?.invoke(false)
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 10000
        }

        fusedLocationProviderClient.requestLocationUpdates(
            request, callback,
            Looper.myLooper()!!
        )
        Log.e("getLocation00", fusedLocationProviderClient.toString())
    }


    private fun updateTimeEndDb(id: Int, time: Long): Int {
        return locationDao.updateTime2ById(id, time)
    }

    private fun updateValueDb(id: Int, newInfo: String): Int {
        return locationDao.updateLocationById(id, newInfo)
    }

    private fun getLastValueFromDb(): LocationDbModel {
        return locationDao.getLastValue(getCurrentDate())
    }

    fun getDate(milliSeconds: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeInMillis(milliSeconds)
        return formatter.format(calendar.getTime())
    }
    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm")
        //val calendar: Calendar = Calendar.getInstance()
       // calendar.timeInMillis = System.currentTimeMillis()
        return formatter.format(System.currentTimeMillis())
    }


    private fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    suspend fun saveLocation(location: Location) {
        val res = LocationDbModel(
            location.time.toString(),
            location.time,
            location.time,
            getDate(location.time),
            location.latitude,
            location.longitude,
            1,
            location.accuracy,
            location.speed
        )
        val itemsCount = locationDao.insertLocation(res)
        _isEnathAccuracy.postValue(true)
        onLocationChangedListener?.invoke(true)
    }


    private inner class Callback : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)

            if (mBestLoc.longitude == 0.0 || result.lastLocation.accuracy < mBestLoc.accuracy) {
                mBestLoc.latitude = result.lastLocation.latitude
                mBestLoc.longitude = result.lastLocation.longitude
                mBestLoc.accuracy = result.lastLocation.accuracy
                mBestLoc.speed = result.lastLocation.speed
                mBestLoc.time = result.lastLocation.time
            }

            if (result.lastLocation != null && result.lastLocation.accuracy < workingTimeModel.accuracy) {
                /*ProcessLifecycleOwner.get().lifecycleScope*/
                externalScope.launch(Dispatchers.IO) {
                    val lastDbValue = getLastValueFromDb()

                    result.lastLocation.let {
                        if (lastDbValue != null) {
                            val locA = Location("lastValue")
                            locA.latitude = lastDbValue.latitude
                            locA.longitude = lastDbValue.longitude
                            val locB = Location("newValue")
                            locB.latitude = it.latitude
                            locB.longitude = it.longitude
                            val dist = locA.distanceTo(locB)
                            Log.e("distanceLastNew", dist.toString())
                            if (dist > workingTimeModel.minDist) {
                                val res = LocationDbModel(
                                    it.time.toString(),
                                    it.time,
                                    null,
                                    getDate(it.time),
                                    it.latitude,
                                    it.longitude,
                                    1,
                                    it.accuracy,
                                    it.speed
                                )
                                val itemsCount = locationDao.insertLocation(res)
                                _isEnathAccuracy.postValue(true)
                                onLocationChangedListener?.invoke(true)
                                Log.e("insertLocation", res.toString())
                            } else {
                                updateTimeEndDb(lastDbValue._id.toInt(), it.time)
                                updateValueDb(
                                    lastDbValue._id.toInt(),
                                    getDate(lastDbValue.datetime.toLong()) + " - " + getDate(it.time)
                                )
                                _isEnathAccuracy.postValue(true)
                                onLocationChangedListener?.invoke(true)
                            }
                        } else {
                            val res = LocationDbModel(
                                it.time.toString(),
                                it.time,
                                null,
                                getDate(it.time),
                                it.latitude,
                                it.longitude,
                                1,
                                it.accuracy,
                                it.speed
                            )
                            val itemsCount = locationDao.insertLocation(res)
                            _isEnathAccuracy.postValue(true)
                            onLocationChangedListener?.invoke(true)

                            Log.e("insertLocationNull", res.toString())
                        }
                    }

                }
            }
        }
    }

    fun stopLocationUpdates() {
        Log.e("getLocationStop", fusedLocationProviderClient.toString())
        fusedLocationProviderClient.removeLocationUpdates(callback)
    }


    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): Location? {
        var ddsf: Location? = null
        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
            ddsf = it
            it.let { Log.e("getflpc", "$it") }
        }
        return ddsf
    }


    override suspend fun stopData(): Int {
        stopLocationUpdates()
        return 1
    }


    val defaultTime = Gson().toJson(
        SettingsDataModel(
            listOf("1", "1", "1", "1", "1", "1", "1"),
            "09:00",
            "17:00",
            200,
            50,
            180,
            180
        )
    )

    var worktime: String?
        get() {
            val k: String?
            if (mSettings.contains(APP_PREFERENCES_worktime)) {
                k = mSettings.getString(
                    APP_PREFERENCES_worktime,
                    defaultTime/*"{\"days\":\"1;1;1;1;1;1;1\",\"start\":\"09:00\",\"end\":\"17:00\"}"*/
                )
            } else
                k = defaultTime
            return k
        }
        @SuppressLint("NewApi")
        set(k) {
            val editor = mSettings.edit()
            editor.putString(APP_PREFERENCES_worktime, k)
            if (android.os.Build.VERSION.SDK_INT > 9) {
                editor.apply()
            } else
                editor.commit()
        }


    private companion object {
        val APP_PREFERENCES_worktime = "worktime"
    }

}