package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.StateFlow
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.entity.*

interface TestsRepository {
	
    fun getQuestionsInfoList(testId:Int,limit:Int): List<TestQuestion>

    fun getTestInfo():List<TestInfo>

    suspend fun loadData():List<Int>
	
	suspend fun saveLocationOnBD(lld:LocationLiveData): Int
	
	suspend fun stopData(): Int

    suspend fun getLocation() : LocationLiveData
    suspend fun getLocation2() : LiveData<Location?>
    suspend fun getLastLocation() : LiveData<Location?>
}
