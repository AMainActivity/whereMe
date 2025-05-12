package ru.ama.whereme.data.repository

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
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
import okhttp3.RequestBody
import ru.ama.ottest.data.mapper.WmMapperJwt
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.data.alarms.PeriodicAlarm
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.database.SettingsDataModel
import ru.ama.whereme.data.database.SettingsUserInfoDataModel
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.mapper.WmMapperByDays
import ru.ama.whereme.data.mapper.WmMapperSettings
import ru.ama.whereme.data.mapper.WmMapperUserInfoSettings
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.ResponseEntity
import ru.ama.whereme.domain.entity.ResponseJwtEntity
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.WmRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject


class WmRepositoryImpl @Inject constructor(
    private val mapper: WmMapper,
    private val mapperByDays: WmMapperByDays,
    private val mapperSetTime: WmMapperSettings,
    private val mapperUserInfoSettings: WmMapperUserInfoSettings,
    private val mapperJwt: WmMapperJwt,
    private val locationDao: LocationDao,
    private val application: Application,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val googleApiAvailability: GoogleApiAvailability,
    private val apiService: WmApiService,
    private val wmSettings: WmSettings
) : WmRepository {


    private lateinit var workingTimeModel: SettingsDomModel
    var mBestLoc = Location(BEST_LOCATION_PROVIDER)
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
        val strDate = sdf.parse(start) as Date
        val endDate = sdf.parse(end) as Date
        if (endDate.time >= strDate.time) {
            res = true
        }
        Log.e("compare2Times", "$strDate ### $endDate %%% $res")
        return res
    }

    fun isCurTimeBetweenSettings(): Boolean {
        val wTime = getWorkingTime()
        return (compare2Times(wTime.start, getCurrentTime()) && compare2Times(
            getCurrentTime(),
            wTime.end
        ))
    }

    override suspend fun checkKod(request: RequestBody): ResponseJwtEntity {
        val responc = apiService.chekcKod(request)
        val mBody = responc.body()?.let { mapperJwt.mapDtoToModel(it) }
        return ResponseJwtEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
    }

    override fun isTimeToGetLocaton(): Boolean {
        var result = false
        val wTime = getWorkingTime()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        when (calendar[Calendar.DAY_OF_WEEK]) {
            Calendar.MONDAY -> {
                result = wTime.days[0] == DEFAULT_SETTINGS_DAY
            }

            Calendar.TUESDAY -> {
                result = wTime.days[1] == DEFAULT_SETTINGS_DAY
            }

            Calendar.WEDNESDAY -> {
                result = wTime.days[2] == DEFAULT_SETTINGS_DAY
            }

            Calendar.THURSDAY -> {
                result = wTime.days[3] == DEFAULT_SETTINGS_DAY
            }

            Calendar.FRIDAY -> {
                result = wTime.days[4] == DEFAULT_SETTINGS_DAY
            }

            Calendar.SATURDAY -> {
                result = wTime.days[5] == DEFAULT_SETTINGS_DAY
            }

            Calendar.SUNDAY -> {
                result = wTime.days[6] == DEFAULT_SETTINGS_DAY
            }
        }
        result = result && isCurTimeBetweenSettings()
        Log.e("IsTimeToGetLocaton", result.toString())
        return result
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

    override fun runAlarmClock() {
        Log.e("runAlarmClock", "AlarmClock")
        val wTime = getWorkingTime()
        val am = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(application, AlarmClockStart::class.java)
        val pi = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, wTime.start.split(SPLIT_DELIMITER)[0].toInt())
            set(Calendar.MINUTE, wTime.start.split(SPLIT_DELIMITER)[1].toInt())
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH) + 1)
        }
        am.cancel(pi)
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            DEFAULT_INTERVAL_ALARM,
            pi
        )
        setWorkingTime(wTime.copy(isEnable = true))
    }

    override fun cancelAlarmClock() {
        Log.e("runAlarmClock", "cancelAlarmClock")
        val intent = Intent(application, AlarmClockStart::class.java)
        val sender = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
        setWorkingTime(getWorkingTime().copy(isEnable = false))
    }

    override fun runAlarm(timeInterval: Long) {
        Log.e("runAlarm", "" + timeInterval)
        val am = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(application, PeriodicAlarm::class.java)
        val pi = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmTimeAtUTC = System.currentTimeMillis() + timeInterval * ONE_SECOND
        am.cancel(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeAtUTC, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmClockInfo: AlarmManager.AlarmClockInfo =
                AlarmManager.AlarmClockInfo(alarmTimeAtUTC, pi)
            am.setAlarmClock(alarmClockInfo, pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        } else {
            am.set(
                AlarmManager.RTC_WAKEUP,
                alarmTimeAtUTC, pi
            )
        }
    }

    override fun cancelAlarm() {
        Log.e("runAlarm", "cancelAlarm")
        val intent = Intent(application, PeriodicAlarm::class.java)
        val sender = PendingIntent.getBroadcast(
            application,
            EMPTY_INT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
    }

    override suspend fun getGropingDays() =
        locationDao.getLocationsByDays().map {
            mapperByDays.mapDbModelToEntity(it)
        }

    override fun getWorkingTime() = mapperSetTime.mapDataModelToDomain(
        Gson().fromJson(
            wmSettings.worktime,
            SettingsDataModel::class.java
        )
    )

    override fun setWorkingTime(dm: SettingsDomModel) {
        wmSettings.worktime = Gson().toJson(mapperSetTime.mapDomainToDataModel(dm))
    }

    override suspend fun getLocationById(mDate: String): LiveData<List<LocationDb>> {
        Log.e("getLocationById", mDate)
        return Transformations.map(locationDao.getLocationsById(mDate)) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }

    override suspend fun checkWmJwToken(request: RequestBody): ResponseEntity {
        val responc = apiService.checkToken(request)
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }
        return ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
    }

    override suspend fun logOut(request: RequestBody): ResponseEntity {
        val responc = apiService.logOut(request)
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }
        return ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
    }

    override suspend fun getLocationsFromBd(): LiveData<List<LocationDb>> =
        Transformations.map(locationDao.getLocations()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }


    fun updateIsWrite(idList: List<Long>) = locationDao.updateQuery(idList)

    suspend fun getLocations4Net(): List<LocationDb> {
        val d = getWmUserInfoSetings().posId
        val dd = d.toString()
        val res = (locationDao.getLocations4Net(
            if (dd.length <= 8) d else (dd.substring(0, 8).toInt())
        )).map { mapper.mapDbModelToEntity(it) }
        Log.e("getLocations4Net", "posid=$d")
        Log.e("getLocations4Net", "LocationDb={$res}")
        return res
    }

    suspend fun writeLoc4Net(request: RequestBody): ResponseEntity {
        val responc = apiService.writeLocDatas(request)
        Log.e("writeLoc4Net", responc.toString())
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }
        return ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        workingTimeModel = getWorkingTime()
        mBestLoc.latitude = EMPTY_DOUBLE
        mBestLoc.longitude = EMPTY_DOUBLE
        mBestLoc.accuracy = DEFAULT_ACCURACY
        mBestLoc.speed = EMPTY_FLOAT
        mBestLoc.time = EMPTY_LONG
        _isEnathAccuracy.value = false
        onLocationChangedListener?.invoke(false)
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = FASTEST_INTERVAL
            fastestInterval = FASTEST_INTERVAL
        }
        Looper.myLooper()?.let {
            fusedLocationProviderClient.requestLocationUpdates(
                request, callback,
                it
            )
        }
        Log.e("getLocation00", fusedLocationProviderClient.toString())
    }

    private fun updateTimeEndDb(id: Int, time: Long) = locationDao.updateTime2ById(id, time)

    private fun updateValueDb(id: Int, newInfo: String) =
        locationDao.updateLocationById(id, newInfo)

    private fun getLastValueFromDb() = locationDao.getLastValue(getCurrentDate())

    fun getDate(milliSeconds: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return formatter.format(calendar.time)
    }

    fun df(): String {
        val curUtc = System.currentTimeMillis()
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = curUtc
        val curCal = formatter.format(calendar.time)
        val curUtc1 = formatter.format(curUtc)
        return "curUtc:$curUtc # curUtc1:$curUtc1 \n cal:${calendar.timeInMillis} # curCal:$curCal"
    }

    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm")
        return formatter.format(System.currentTimeMillis())
    }

    fun getCurrentDateMil(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(System.currentTimeMillis())
    }

    fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    suspend fun saveLocation(location: Location, lTime: Long) {
        val res = LocationDbModel(
            lTime.toString(),
            lTime,
            lTime,
            getDate(lTime),
            location.latitude,
            location.longitude,
            ONE_INT,
            location.accuracy,
            location.speed,
            EMPTY_INT
        )
        locationDao.insertLocation(res)
        _isEnathAccuracy.postValue(true)
        onLocationChangedListener?.invoke(true)
    }

    private inner class Callback : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            val lTime = System.currentTimeMillis()
            if (mBestLoc.longitude == EMPTY_DOUBLE || result.lastLocation.accuracy < mBestLoc.accuracy) {
                mBestLoc.latitude = result.lastLocation.latitude
                mBestLoc.longitude = result.lastLocation.longitude
                mBestLoc.accuracy = result.lastLocation.accuracy
                mBestLoc.speed = result.lastLocation.speed
                mBestLoc.time = lTime
            }
            if (result.lastLocation != null && result.lastLocation.accuracy < workingTimeModel.accuracy) {
                /*ProcessLifecycleOwner.get().lifecycleScope*/
                externalScope.launch(Dispatchers.IO) {
                    val lastDbValue = getLastValueFromDb()

                    result.lastLocation.let {
                        if (lastDbValue != null) {
                            val locA = Location(LOCATION_A)
                            locA.latitude = lastDbValue.latitude
                            locA.longitude = lastDbValue.longitude
                            val locB = Location(LOCATION_B)
                            locB.latitude = it.latitude
                            locB.longitude = it.longitude
                            val dist = locA.distanceTo(locB)
                            Log.e("distanceLastNew", dist.toString())
                            if (dist > workingTimeModel.minDist) {
                                val res = LocationDbModel(
                                    lTime.toString(),
                                    lTime,
                                    null,
                                    getDate(lTime),
                                    it.latitude,
                                    it.longitude,
                                    ONE_INT,
                                    it.accuracy,
                                    it.speed,
                                    EMPTY_INT
                                )
                                val itemsCount = locationDao.insertLocation(res)
                                _isEnathAccuracy.postValue(true)
                                onLocationChangedListener?.invoke(true)
                                Log.e("insertLocation", res.toString())
                            } else {
                                updateTimeEndDb(lastDbValue._id.toInt(), lTime)
                                updateValueDb(
                                    lastDbValue._id.toInt(),
                                    getDate(lastDbValue.datetime.toLong()) + " - " + getDate(lTime)
                                )
                                _isEnathAccuracy.postValue(true)
                                onLocationChangedListener?.invoke(true)
                            }
                        } else {
                            val res = LocationDbModel(
                                lTime.toString(),
                                lTime,
                                null,
                                getDate(lTime),
                                it.latitude,
                                it.longitude,
                                ONE_INT,
                                it.accuracy,
                                it.speed, EMPTY_INT
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

    override fun getWmUserInfoSetings() = mapperUserInfoSettings.mapDataModelToDomain(
        Gson().fromJson(
            wmSettings.jwToken,
            SettingsUserInfoDataModel::
            class.java
        )
    )


    override fun setWmUserInfoSetings(dm: SettingsUserInfoDomModel) {
        wmSettings.jwToken = Gson().toJson(mapperUserInfoSettings.mapDomainToDataModel(dm))
    }

    private companion object {
        const val BEST_LOCATION_PROVIDER = "bestLocationOfBadAccuracy"
        const val LOCATION_A = "lastValue"
        const val LOCATION_B = "newValue"
        const val DEFAULT_SETTINGS_DAY = "1"
        const val SPLIT_DELIMITER = ":"
        const val ONE_SECOND = 1000L
        const val DEFAULT_INTERVAL_ALARM = 24 * 60 * 60 * ONE_SECOND
        const val EMPTY_INT = 0
        const val ONE_INT = 1
        const val EMPTY_LONG = 0L
        const val EMPTY_FLOAT = 0f
        const val EMPTY_DOUBLE = 0.0
        const val FASTEST_INTERVAL = 10 * ONE_SECOND
        const val DEFAULT_ACCURACY = 1500f
    }
}