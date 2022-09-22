package ru.ama.whereme.presentation

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class MaViewModel @Inject constructor(
    private val runAlarmUseCase: RunAlarmUseCase,
    private val getIsActivateUseCase: GetIsActivateUseCase
) : ViewModel() {

private val _isSuccess = MutableLiveData<Boolean>()
    val isSuccess: LiveData<Boolean>
        get() = _isSuccess
		
		
init {
	//_isSuccess.value=getIsActivateUseCase()
}
    fun checkIsActivate()=getIsActivateUseCase()
    

}