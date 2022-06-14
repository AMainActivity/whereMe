package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val getLocationsFromBdUseCase: GetLocationsFromBdUseCase,
    private val runWorkerUpdateUseCase: RunWorkerUpdateUseCase
) : ViewModel() {

     var lld2 : LiveData<List<LocationDb>>?=null

    init {
      
viewModelScope.launch {
    runWorkerUpdateUseCase(15)
    Log.e("runWorker1","15")
        lld2 = getLocationsFromBdUseCase()
}
		
    }


    companion object {}
}