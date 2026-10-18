package ru.ama.whereme.data.repository

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
import android.util.Log
import androidx.room.withTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import ru.ama.ottest.data.mapper.WmMapperJwt
import ru.ama.ottest.data.network.WmApiService
import ru.ama.whereme.data.alarms.AlarmClockStart
import ru.ama.whereme.data.alarms.PeriodicAlarm
import ru.ama.whereme.data.database.AppDatabase
import ru.ama.whereme.data.location.FusedLocationSource
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.database.SettingsDataModel
import ru.ama.whereme.data.database.SettingsUserInfoDataModel
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.mapper.WmMapperByDays
import ru.ama.whereme.data.mapper.WmMapperSettings
import ru.ama.whereme.data.mapper.WmMapperUserInfoSettings
import javax.inject.Singleton
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


@Singleton
class WmRepositoryImpl @Inject constructor(
    private val mapper: WmMapper,
    private val mapperByDays: WmMapperByDays,
    private val mapperSetTime: WmMapperSettings,
    private val mapperUserInfoSettings: WmMapperUserInfoSettings,
    private val mapperJwt: WmMapperJwt,
    private val locationDao: LocationDao,
    private val application: Application,
    private val locationSource: FusedLocationSource,
    private val database: AppDatabase,
    private val apiService: WmApiService,
    private val wmSettings: WmSettings
) : WmRepository {


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
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun runAlarmClock() {
        setWorkingTime(getWorkingTime().copy(isEnable = true))
        scheduleNextAlarmClockIfEnabled()
    }

    fun scheduleNextAlarmClockIfEnabled() {
        val wTime = getWorkingTime()
        if (!wTime.isEnable) return
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
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                calendar.timeInMillis,
                pi
            ),
            pi
        )
        /* am.setRepeating(
             AlarmManager.RTC_WAKEUP,
             calendar.timeInMillis,
             DEFAULT_INTERVAL_ALARM,
             pi
         )*/
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
        return locationDao.getLocationsById(mDate).map { locations ->
            locations.map { mapper.mapDbModelToEntity(it) }
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
        locationDao.getLocations().map { locations ->
            locations.map { mapper.mapDbModelToEntity(it) }
        }


    suspend fun collectLocation(settings: SettingsDomModel): Location? =
        locationSource.collectBestLocation(
            settings.accuracy,
            settings.timeOfWaitAccuracy.toLong().coerceAtLeast(1L) * ONE_SECOND
        )

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

    suspend fun saveLocation(
        location: Location, lTime: Long, settings: SettingsDomModel, previousRowId: Long?
    ): Long = withContext(Dispatchers.IO) {
            database.withTransaction {
                val last = locationDao.getLastValue(getCurrentDate())
                val previous = last?.let {
                    Location(LOCATION_A).apply { latitude = it.latitude; longitude = it.longitude }
                }
                val gap = last?.let { lTime - (it.dateend ?: it.datestart) }
                val maxGap = (settings.timeOfWorkingWM.toLong().coerceAtLeast(1L) +
                    settings.timeOfWaitAccuracy.toLong().coerceAtLeast(1L)) * ONE_SECOND
                if (last != null && last._id == previousRowId && previous != null &&
                    gap != null && gap in 0..maxGap && previous.distanceTo(location) <= settings.minDist
                ) {
                    locationDao.updateInterval(
                        last._id, lTime, getDate(last.datestart) + " - " + getDate(lTime)
                    )
                    last._id
                } else {
                    locationDao.insertLocation(
                        LocationDbModel(
                            lTime.toString(), lTime, null, getDate(lTime), location.latitude,
                            location.longitude, ONE_INT, location.accuracy, location.speed, EMPTY_INT
                        )
                    )
                }
            }
        }

    fun stopLocationUpdates() = locationSource.stop()

    override suspend fun getLastLocation(): Location? = locationSource.lastLocation()

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
        const val LOCATION_A = "lastValue"
        const val DEFAULT_SETTINGS_DAY = "1"
        const val SPLIT_DELIMITER = ":"
        const val ONE_SECOND = 1000L
        const val DEFAULT_INTERVAL_ALARM = 24 * 60 * 60 * ONE_SECOND
        const val EMPTY_INT = 0
        const val ONE_INT = 1
    }
}