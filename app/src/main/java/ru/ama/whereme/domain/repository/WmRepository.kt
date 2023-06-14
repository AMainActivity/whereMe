package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import okhttp3.RequestBody
import ru.ama.whereme.domain.entity.*

interface WmRepository {
    suspend fun getLocationsFromBd(): LiveData<List<LocationDb>>
    suspend fun getLocationById(mDate: String): LiveData<List<LocationDb>>
    suspend fun getGropingDays(): List<LocationDbByDays>
    fun isInternetConnected(): Boolean

    suspend fun stopData(): Int
    fun isMyServiceRunning(serviceClass: Class<*>): Boolean
    fun isTimeToGetLocaton(): Boolean
    fun runAlarm(timeInterval: Long)
    fun runAlarmClock()
    fun cancelAlarm()
    fun cancelAlarmClock()

    fun getWorkingTime(): SettingsDomModel
    fun setWorkingTime(dm: SettingsDomModel)

    fun getWmUserInfoSetings(): SettingsUserInfoDomModel
    fun setWmUserInfoSetings(set: SettingsUserInfoDomModel)

    suspend fun checkWmJwToken(request: RequestBody): ResponseEntity
    suspend fun logOut(request: RequestBody): ResponseEntity

    suspend fun getLastLocation(): Location?
    suspend fun checkKod(request: RequestBody): ResponseJwtEntity
}
