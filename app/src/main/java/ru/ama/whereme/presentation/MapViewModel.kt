package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocationsFromBdUseCase: GetLocationsFromBdUseCase,
    private val getGropingDaysUseCase: GetGropingDaysUseCase,
    private val getLocationsFromBdByIdUseCase: GetLocationsFromBdByIdUseCase,
    private val checkInternetConnectionUseCase: CheckInternetConnectionUseCase
) : ViewModel() {

    var lld2: LiveData<List<LocationDb>>? = null
    var lldByDay: LiveData<List<LocationDb>>? = null
    var ld_days: List<LocationDbByDays>? = null

    init {

        viewModelScope.launch {
            //  runWorkerUpdateUseCase(10)
            // Log.e("runWorker1","15")
            //delay(3*1000)
            lld2 = getLocationsFromBdUseCase()
            //  ld_days=getGropingDaysUseCase()
        }

    }

    fun getListOfDays(): List<LocationDbByDays>? {
        viewModelScope.launch { ld_days = getGropingDaysUseCase() }
        return ld_days
    }

    fun isInternetConnected() = checkInternetConnectionUseCase()


    fun getDataByDate(mDate: String) {
        viewModelScope.launch {
            lldByDay = getLocationsFromBdByIdUseCase(mDate)
        }
    }

}