package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.StateFlow
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.entity.*

interface WmRepository {


    suspend fun loadData():List<Int>
    suspend fun GetLocationsFromBd():LiveData<List<LocationDb>>
	
	suspend fun saveLocationOnBD(lld:LocationLiveData): Int
	
	suspend fun stopData(): Int
	fun runWorker(timeInterval:Long)

    suspend fun getLocation() : LocationLiveData
    suspend fun getLocation2() : LiveData<Location?>
    suspend fun getLastLocation() : LiveData<Location?>
}
