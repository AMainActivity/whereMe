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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import ru.ama.ottest.data.mapper.WmMapperJwt
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.data.database.*
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.mapper.WmMapperByDays
import ru.ama.whereme.data.mapper.WmMapperSettings
import ru.ama.whereme.data.mapper.WmMapperUserInfoSettings
import ru.ama.whereme.data.workers.Alarm
import ru.ama.whereme.data.workers.AlarmClockStart
import ru.ama.whereme.di.ApplicationScope
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.WmRepository
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*
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

        val res = ResponseJwtEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )

        /* nBody = sd.body()?.let { mapperJwt.mapDtoToModel(it) }
         nError = sd.errorBody()?.let { it }
         h: Response<JsonJwt> = sd.raw()*/
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
        val pi = PendingIntent.getBroadcast(application, 0, i, 0)
        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, wTime.start.split(":")[0].toInt())
            set(Calendar.MINUTE, wTime.start.split(":")[1].toInt())
        }
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH) + 1)
        }
        am.cancel(pi)
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            24 * 60 * 60 * 1000,
            pi
        )
        setWorkingTime(wTime.copy(isEnable = true))
    }

    override fun cancelAlarmClock() {
        Log.e("runAlarmClock", "cancelAlarmClock")
        val intent = Intent(application, AlarmClockStart::class.java)
        val sender = PendingIntent.getBroadcast(application, 0, intent, 0)
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
        setWorkingTime(getWorkingTime().copy(isEnable = false))
        //cancelAlarm()
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

    override suspend fun checkWmJwToken(request: RequestBody): ResponseEntity {
        val responc = apiService.checkToken(request)
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }

        val res = ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
        return res
    }

    override suspend fun logOut(request: RequestBody): ResponseEntity {
        val responc = apiService.logOut(request)
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }

        val res = ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
        return res
    }

    override suspend fun GetLocationsFromBd(): LiveData<List<LocationDb>> {
        return Transformations.map(locationDao.getLocations()) {
            it.map {
                mapper.mapDbModelToEntity(it)
            }
        }
    }

fun updateIsWrite(idList: List<Long>) {
        return locationDao.updateQuery(idList)
    }

    suspend fun getLocations4Net(): List<LocationDb> {
        val res = (locationDao.getLocations4Net()).map { mapper.mapDbModelToEntity(it) }	
        return res
    }
	
	
	 suspend fun writeLoc4Net(request: RequestBody): ResponseEntity {
        val responc = apiService.writeLocDatas(request)
         Log.e("writeLoc4Net", responc.toString())
        val mBody = responc.body()?.let { mapperJwt.mapAllDtoToModel(it) }

        val res = ResponseEntity(
            mBody,
            responc.isSuccessful,
            responc.errorBody(),
            responc.code()
        )
        return res
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
    fun getLastValue1(): List<LocationDbModel> {
        return locationDao.getLastValu1e()
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

     fun getCurrentDateMil(): String {
         val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(System.currentTimeMillis())
    }
     fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    suspend fun saveLocation(location: Location,lTime:Long) {
        val res = LocationDbModel(
            lTime.toString(),
            lTime,
            lTime,
            getDate(lTime),
            location.latitude,
            location.longitude,
            1,
            location.accuracy,
            location.speed,
            0
        )
        val itemsCount = locationDao.insertLocation(res)
        _isEnathAccuracy.postValue(true)
        onLocationChangedListener?.invoke(true)
    }


    private inner class Callback : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
val lTime=System.currentTimeMillis()
            if (mBestLoc.longitude == 0.0 || result.lastLocation.accuracy < mBestLoc.accuracy) {
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
                                    lTime.toString(),
                                    lTime,
                                    null,
                                    getDate(lTime),
                                    it.latitude,
                                    it.longitude,
                                    1,
                                    it.accuracy,
                                    it.speed,
                                    0
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
                                1,
                                it.accuracy,
                                it.speed, 0
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
            180,
            false
        )
    )
    val defaultUserInfo = Gson().toJson(
        SettingsUserInfoDataModel(
            "",
            0,
            0,
            "",
            "",
            false
        )
    )
    override fun getWmUserInfoSetings(): SettingsUserInfoDomModel {
        return mapperUserInfoSettings.mapDataModelToDomain(
            Gson().fromJson(
                jwToken,
                SettingsUserInfoDataModel::class.java
            )
        )
    }

    override fun setWmUserInfoSetings(dm: SettingsUserInfoDomModel) {
        jwToken = Gson().toJson(mapperUserInfoSettings.mapDomainToDataModel(dm))
    }
    /*override fun getWmJwToken() = jwToken

    override fun setWmJwToken(jwt: String) {
        jwToken = jwt
    }

    override fun getIsActivate() = isActivate

    override fun setIsActivate(b: Boolean) {
        isActivate = b
    }*/

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
   var jwToken: String
        get() {
            val k: String
            if (mSettings.contains(APP_PREFERENCES_jwt)) {
                k = mSettings.getString(
                    APP_PREFERENCES_jwt,
                    defaultUserInfo
                ).toString()
            } else
                k = defaultUserInfo
            return k
        }
        @SuppressLint("NewApi")
        set(k) {
            val editor = mSettings.edit()
            editor.putString(APP_PREFERENCES_jwt, k)
            if (android.os.Build.VERSION.SDK_INT > 9) {
                editor.apply()
            } else
                editor.commit()
        }
    /*var isActivate: Boolean
        get() {
            val k: Boolean
            if (mSettings.contains(APP_PREFERENCES_IS_ACTIVATE)) {
                k = mSettings.getBoolean(
                    APP_PREFERENCES_IS_ACTIVATE,
                    false
                )
            } else
                k = false
            return k
        }
        @SuppressLint("NewApi")
        set(k) {
            val editor = mSettings.edit()
            editor.putBoolean(APP_PREFERENCES_IS_ACTIVATE, k)
            if (android.os.Build.VERSION.SDK_INT > 9) {
                editor.apply()
            } else
                editor.commit()
        }*/

    private companion object {
        val APP_PREFERENCES_worktime = "worktime"
        val APP_PREFERENCES_jwt = "jwt"
        val APP_PREFERENCES_IS_ACTIVATE = "IS_ACTIVATE"
    }

}