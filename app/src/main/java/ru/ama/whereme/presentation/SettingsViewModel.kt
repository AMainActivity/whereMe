package ru.ama.whereme.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.tracking.TrackingScheduler
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository, private val scheduler: TrackingScheduler,
    @ApplicationScope applicationScope: CoroutineScope
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val settings = repository.settings.loadState(viewModelScope, retry)
    private val failures = MutableLiveData<Int>()
    val errors: LiveData<Int> = failures
    private val commands = SettingsWriteQueue(applicationScope) { failures.postValue(R.string.storage_write_failed) }
    fun retrySettings() { retry.value++ }
    fun update(transform: (SettingsDomModel) -> SettingsDomModel) {
        commands.submit {
            val updated = repository.updateSettings(transform)
            if (updated.isEnable) scheduler.scheduleNextAlarmClockIfEnabled()
        }
    }
    fun setSchedule(enabled: Boolean) {
        commands.submit { if (enabled) scheduler.runAlarmClock() else scheduler.cancelAlarmClock() }
    }
    fun checkService() = scheduler.isMyServiceRunning(MyForegroundService::class.java)
    fun cancelAlarmService() = scheduler.cancelAlarm()
    override fun onCleared() { commands.close(); super.onCleared() }
}
