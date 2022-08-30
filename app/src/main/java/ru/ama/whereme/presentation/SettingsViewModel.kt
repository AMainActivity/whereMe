package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import ru.ama.whereme.data.database.SettingsDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val getWorkingTimeUseCase: GetWorkingTimeUseCase,
    private val сheckServiceUseCase: CheckServiceUseCase,
    private val setWorkingTimeUseCase: SetWorkingTimeUseCase,
    private val runAlarmClockUseCase: RunAlarmClockUseCase,
    private val cancalAlarmClockUseCase: CancalAlarmClockUseCase,
    private val isTimeToGetLocatonUseCase: IsTimeToGetLocatonUseCase

) : ViewModel() {

    init {
        Log.e("SettingsViewModel", getWorkingTimeUseCase().toString())
    }

    fun getWorkingTime(): SettingsDomModel {
        return getWorkingTimeUseCase()
    }


fun runAlarmClock()
{
	runAlarmClockUseCase()
}
fun cancelAlarmClock()
{
    cancalAlarmClockUseCase()
}

    fun сheckService(): Boolean {
        Log.e("fromSet", сheckServiceUseCase(MyForegroundService::class.java).toString())
        return сheckServiceUseCase(MyForegroundService::class.java)
    }

    fun isTimeToGetLocaton():Boolean{
        return isTimeToGetLocatonUseCase()
    }

    fun setWorkingTime(dm: SettingsDomModel) {
        setWorkingTimeUseCase(dm)
    }


}