package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import ru.ama.whereme.data.database.SettingsDomModel
import ru.ama.whereme.domain.entity.*

interface WmRepository {


    suspend fun loadData(): List<Int>
    suspend fun GetLocationsFromBd(): LiveData<List<LocationDb>>
    suspend fun getLocationById(mDate: String): LiveData<List<LocationDb>>
    suspend fun getGropingDays(): List<LocationDbByDays>
    fun isInternetConnected(): Boolean

    suspend fun stopData(): Int
    fun runWorker(timeInterval: Long)
    fun runAlarm(timeInterval: Long)
    fun cancelAlarm()

    fun getWorkingTime(): SettingsDomModel
    fun setWorkingTime(dm:SettingsDomModel)

    suspend fun getLastLocation(): Location?
}
