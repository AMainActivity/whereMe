package ru.ama.whereme.presentation

import androidx.lifecycle.SavedStateHandle
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.tracking.TrackingAction

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    private val savedState: SavedStateHandle, coordinator: TrackingCoordinator,
    @ApplicationScope applicationScope: CoroutineScope
) : ViewModel() {
    val tracking = coordinator.state
    var pendingTrackingAction: TrackingAction?
        get() = savedState.get<String>("tracking_action")?.let(TrackingAction::valueOf)
        set(value) { savedState["tracking_action"] = value?.name }
    var waitingForAccessResult: Boolean
        get() = savedState["tracking_waiting"] ?: false
        set(value) { savedState["tracking_waiting"] = value }
    fun cancelTrackingRequest() { pendingTrackingAction = null; waitingForAccessResult = false }
    private val retry = MutableStateFlow(0)
    val settings = repository.settings.loadState(viewModelScope, retry)
    private val failures = MutableLiveData<Int>()
    val errors: LiveData<Int> = failures
    private val commands = SettingsWriteQueue(applicationScope) { failures.postValue(R.string.storage_write_failed) }
    init {
        applicationScope.launch {
            try { coordinator.restoreAlarms(refreshRunning = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { failures.postValue(R.string.tracking_restore_failed) }
        }
    }
    fun retrySettings() { retry.value++ }
    fun update(transform: (SettingsDomModel) -> SettingsDomModel) {
        commands.submit {
            val updated = repository.updateSettings { current ->
                val candidate = transform(current)
                require(!candidate.isEnable || (candidate.start != candidate.end && candidate.days.any { it == "1" }))
                candidate
            }
            if (updated.isEnable) scheduler.scheduleNextAlarmClockIfEnabled()
        }
    }
    fun setSchedule(enabled: Boolean) {
        commands.submit { if (enabled) scheduler.runAlarmClock() else scheduler.cancelAlarmClock() }
    }
    override fun onCleared() { commands.close(); super.onCleared() }
}
