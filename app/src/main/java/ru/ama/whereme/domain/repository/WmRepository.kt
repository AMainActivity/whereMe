package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import okhttp3.RequestBody
import retrofit2.Response
import ru.ama.whereme.data.database.SettingsDomModel
import ru.ama.whereme.data.network.model.JsonJwtDto
import ru.ama.whereme.domain.entity.*

interface WmRepository {


    suspend fun loadData(): List<Int>
    suspend fun GetLocationsFromBd(): LiveData<List<LocationDb>>
    suspend fun getLocationById(mDate: String): LiveData<List<LocationDb>>
    suspend fun getGropingDays(): List<LocationDbByDays>
    fun isInternetConnected(): Boolean

    suspend fun stopData(): Int
    fun isMyServiceRunning(serviceClass: Class<*>): Boolean
    fun IsTimeToGetLocaton(): Boolean
    fun runAlarm(timeInterval: Long)
    fun runAlarmClock()
    fun cancelAlarm()
    fun cancelAlarmClock()

    fun getWorkingTime(): SettingsDomModel
    fun setWorkingTime(dm:SettingsDomModel)

    suspend fun getLastLocation(): Location?
    suspend fun checkKod(request : RequestBody): Response<JsonJwtDto>
}
