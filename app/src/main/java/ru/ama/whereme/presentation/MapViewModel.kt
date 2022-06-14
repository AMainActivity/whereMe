package ru.ama.whereme.presentation

import android.location.Location
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ru.ama.whereme.data.location.LocationLiveData
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocationsFromBd: GetLocationsFromBd,
    private val runWorkerUpdateUseCase: RunWorkerUpdateUseCase
) : ViewModel() {

     var lld2 : LiveData<List<LocationDb>>?=null

    init {
      
viewModelScope.launch {
    runWorkerUpdateUseCase(15)
    Log.e("runWorker1","15")
        lld2 = getLocationsFromBd()
}
		
    }


    companion object {}
}