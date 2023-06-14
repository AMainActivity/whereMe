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

    init {
        Log.e("SettingsViewModel", getWorkingTimeUseCase().toString())
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
}