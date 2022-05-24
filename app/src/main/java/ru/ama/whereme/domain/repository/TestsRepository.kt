package ru.ama.whereme.domain.repository

import android.location.Location
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.StateFlow
import ru.ama.whereme.domain.entity.*

interface TestsRepository {
	
    fun getQuestionsInfoList(testId:Int,limit:Int): List<TestQuestion>

    fun getTestInfo():List<TestInfo>

    suspend fun loadData():List<Int>
    fun getLocation(): StateFlow<Location?>
}
