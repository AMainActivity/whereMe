package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val getWorkingTimeUseCase: GetWorkingTimeUseCase,
    private val checkServiceUseCase: CheckServiceUseCase,
    private val setWorkingTimeUseCase: SetWorkingTimeUseCase,
    private val runAlarmClockUseCase: RunAlarmClockUseCase,
    private val cancelAlarmClockUseCase: CancelAlarmClockUseCase,
    private val cancelAlarmServiceUseCase: CancelAlarmServiceUseCase

) : ViewModel() {

    private val _errorMinDistance = MutableLiveData<Boolean>()
    val errorMinDistance: LiveData<Boolean>
        get() = _errorMinDistance
    private val _errorAccuracy = MutableLiveData<Boolean>()
    val errorAccuracy: LiveData<Boolean>
        get() = _errorAccuracy
    private val _errorTimeAccuracy = MutableLiveData<Boolean>()
    val errorTimeAccuracy: LiveData<Boolean>
        get() = _errorTimeAccuracy
    private val _errorTimePeriod = MutableLiveData<Boolean>()
    val errorTimePeriod: LiveData<Boolean>
        get() = _errorTimePeriod

    fun validateInputData(name: String, idData: SettingsViewNames) {
        when (idData) {
            SettingsViewNames.MIN_DISTANCE -> {
                if (name.isNotEmpty()) {
                    if (name.toInt() >= MIN_DIST_LENGTH) {
                        setWorkingTime(
                            getWorkingTime().copy(
                                minDist = name.toInt()
                            )
                        )
                        _errorMinDistance.value = false
                    } else
                        _errorMinDistance.value = true
                } else
                    _errorMinDistance.value = true
            }
            SettingsViewNames.ACCURACY -> {
                if (name.isNotEmpty()) {
                    if (name.toInt() >= ACCURACY_LENGTH) {
                        setWorkingTime(
                            getWorkingTime().copy(
                                accuracy = name.toInt()
                            )
                        )
                        _errorAccuracy.value = false
                    } else
                        _errorAccuracy.value = true
                } else
                    _errorAccuracy.value = true
            }
            SettingsViewNames.TIME_ACCURACY -> {
                if (name.isNotEmpty()) {
                    if (name.toInt() >= TIME_ACCURACY_LENGTH) {
                        setWorkingTime(
                            getWorkingTime().copy(
                                timeOfWaitAccuracy = name.toInt()
                            )
                        )
                        _errorTimeAccuracy.value = false
                    } else
                        _errorTimeAccuracy.value = true
                } else
                    _errorTimeAccuracy.value = true
            }
            SettingsViewNames.TIME_PERIOD -> {
                if (name.isNotEmpty()) {
                    if (name.toInt() >= TIME_PERIODIC_LENGTH) {
                        setWorkingTime(
                            getWorkingTime().copy(
                                timeOfWorkingWM = name.toInt()
                            )
                        )
                        _errorTimePeriod.value = false
                    } else
                        _errorTimePeriod.value = true
                } else
                    _errorTimePeriod.value = true
            }
        }
    }

    fun resetError(idData: SettingsViewNames) {
        when (idData) {
            SettingsViewNames.MIN_DISTANCE -> _errorMinDistance.value = false
            SettingsViewNames.ACCURACY -> _errorAccuracy.value = false
            SettingsViewNames.TIME_ACCURACY -> _errorTimeAccuracy.value = false
            SettingsViewNames.TIME_PERIOD -> _errorTimePeriod.value = false
        }

    }

    fun getWorkingTime(): SettingsDomModel {
        return getWorkingTimeUseCase()
    }

    fun runAlarmClock() {
        runAlarmClockUseCase()
    }

    fun cancelAlarmClock() {
        cancelAlarmClockUseCase()
    }

    fun cancelAlarmService() {
        cancelAlarmServiceUseCase()
    }

    fun checkService(): Boolean {
        Log.e("fromSet", checkServiceUseCase(MyForegroundService::class.java).toString())
        return checkServiceUseCase(MyForegroundService::class.java)
    }

    fun setWorkingTime(dm: SettingsDomModel) {
        setWorkingTimeUseCase(dm)
    }

    private companion object {
        private const val MIN_DIST_LENGTH = 10
        private const val ACCURACY_LENGTH = 50
        private const val TIME_ACCURACY_LENGTH = 20
        private const val TIME_PERIODIC_LENGTH = 15
    }
}