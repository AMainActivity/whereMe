package ru.ama.whereme.presentation

import android.util.Log
import androidx.lifecycle.*
import ru.ama.whereme.data.database.SettingsDomModel
import ru.ama.whereme.domain.usecase.*
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val getWorkingTimeUseCase: GetWorkingTimeUseCase,
    private val сheckServiceUseCase: CheckServiceUseCase,
    private val setWorkingTimeUseCase: SetWorkingTimeUseCase

) : ViewModel() {

    init {
        Log.e("SettingsViewModel", getWorkingTimeUseCase().toString())
    }

    fun getWorkingTime(): SettingsDomModel {
        return getWorkingTimeUseCase()
    }


    fun сheckService(): Boolean {
        return сheckServiceUseCase(MyForegroundService::class.java)
    }

    fun setWorkingTime(dm: SettingsDomModel) {
        setWorkingTimeUseCase(dm)
    }


}