package ru.ama.whereme.presentation

import androidx.lifecycle.SavedStateHandle
import ru.ama.whereme.domain.tracking.TrackingCoordinator
import ru.ama.whereme.tracking.TrackingAction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asStateFlow
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
    private val failures = MutableStateFlow<Int?>(null)
    val errorMessage = failures.asStateFlow()
    private val mutableDraft = MutableStateFlow<MeasurementDraft?>(
        savedState.get<ArrayList<String>>("measurement_draft")?.let { MeasurementDraft(it[0], it[1], it[2], it[3]) })
    val draft = mutableDraft.asStateFlow()
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()
    private val commands = SettingsWriteQueue(applicationScope + Dispatchers.Main.immediate) {
        saving.value = false
        failures.value = R.string.storage_write_failed
    }
    init {
        viewModelScope.launch {
            settings.collect { state ->
                if (state is LoadState.Ready && mutableDraft.value == null) setDraft(MeasurementDraft.from(state.value))
            }
        }
    }
    private fun setDraft(value: MeasurementDraft) {
        mutableDraft.value = value
        savedState["measurement_draft"] = arrayListOf(value.distance, value.accuracy, value.wait, value.period)
    }
    fun editMeasurement(field: MeasurementField, value: String) {
        mutableDraft.value?.let { setDraft(it.edit(field, value)) }
    }
    fun applyMeasurements() {
        val value = mutableDraft.value ?: return
        if (!value.isValid || saving.value) return
        val stored = (settings.value as? LoadState.Ready)?.value ?: return
        if (value == MeasurementDraft.from(stored)) return
        saving.value = true
        commands.submit {
            val updated = repository.updateSettings(value::applyTo)
            if (mutableDraft.value == value) setDraft(MeasurementDraft.from(updated))
            failures.value = null
            saving.value = false
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
            if (updated.isEnable) scheduler.refreshEnabledSchedule()
            failures.value = null
        }
    }
    fun setSchedule(enabled: Boolean) {
        commands.submit {
            if (enabled) scheduler.runAlarmClock() else scheduler.cancelAlarmClock()
            failures.value = null
        }
    }
    override fun onCleared() { commands.close(); super.onCleared() }
}
