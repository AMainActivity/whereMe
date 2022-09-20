package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.usecase.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocationsFromBdUseCase: GetLocationsFromBdUseCase,
    private val getGropingDaysUseCase: GetGropingDaysUseCase,
    private val getLocationsFromBdByIdUseCase: GetLocationsFromBdByIdUseCase,
    private val checkInternetConnectionUseCase: CheckInternetConnectionUseCase
) : ViewModel() {

    var lld2: LiveData<List<LocationDb>>? = null
    var lldByDay: LiveData<List<LocationDb>>? = null
    //var ld_days: LiveData<List<LocationDbByDays>>? = null

    private val _ld_days = MutableLiveData<List<LocationDbByDays>>()
    val ld_days: LiveData<List<LocationDbByDays>>
        get() = _ld_days

    init {

        viewModelScope.launch {
            //  runWorkerUpdateUseCase(10)
            // Log.e("runWorker1","15")
            //delay(3*1000)
            lld2 = getLocationsFromBdUseCase()
            _ld_days.value = getGropingDaysUseCase()
            //  ld_days=getGropingDaysUseCase()
        }
getDataByDate(getCurrentDate())
    }

    /*fun getListOfDays(): List<LocationDbByDays>? {
        viewModelScope.async { _ld_days.value = getGropingDaysUseCase() }
        return _ld_days
    }*/

    fun isInternetConnected() = checkInternetConnectionUseCase()
	
 fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        return formatter.format(Date())
    }

    fun getDataByDate(mDate: String) {
        viewModelScope.launch {
            lldByDay = getLocationsFromBdByIdUseCase(mDate)
        }
    }

}