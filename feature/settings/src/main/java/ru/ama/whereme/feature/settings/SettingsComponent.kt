package ru.ama.whereme.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.state.*
import ru.ama.whereme.domain.tracking.*

/** State belongs to the tab component, not to its current Compose view. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsComponent(
    context: ComponentContext,
    private val repository: SettingsRepository,
    private val control: TrackingControl,
    applicationScope: CoroutineScope,
) : ComponentContext by context {
    private val scope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val restored = stateKeeper.consume(SETTINGS_STATE_KEY, Snapshot.serializer())
    private val mutable =
        MutableStateFlow(SettingsState(draft = restored?.draft, accessRequest = restored?.action))
    val state = mutable.asStateFlow()
    private val retry = MutableStateFlow(0)
    private val commands =
        SettingsWriteQueue(applicationScope + Dispatchers.Main.immediate) {
            dispatch(
                SettingsResult.Error(
                    if (it is IllegalArgumentException) R.string.tracking_invalid_schedule
                    else R.string.settings_write_error
                )
            )
        }

    init {
        stateKeeper.register(SETTINGS_STATE_KEY, Snapshot.serializer()) {
            Snapshot(state.value.draft, state.value.accessRequest)
        }
        lifecycle.doOnDestroy { commands.close() }
        scope.launch {
            retry
                .flatMapLatest { repository.settings.asLoadState() }
                .collect { dispatch(SettingsResult.Loaded(it)) }
        }
        scope.launch { control.state.collect { dispatch(SettingsResult.Tracking(it)) } }
        scope.launch {
            retry
                .flatMapLatest { repository.themeMode.asLoadState() }
                .collect {
                    when (it) {
                        is LoadState.Ready -> dispatch(SettingsResult.Theme(it.value))
                        LoadState.Failed ->
                            dispatch(SettingsResult.Error(R.string.settings_theme_error))
                        LoadState.Loading -> Unit
                    }
                }
        }
    }

    fun capabilities() = control.capabilities()

    fun accept(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.Edit ->
                state.value.draft?.let {
                    dispatch(SettingsResult.Draft(it.edit(intent.field, intent.text)))
                }
            SettingsIntent.Apply -> {
                val before = state.value
                val draft = before.draft ?: return
                val stored = (before.settings as? LoadState.Ready)?.value ?: return
                if (!draft.isValid || before.saving || draft == MeasurementDraft.from(stored))
                    return
                dispatch(SettingsResult.Saving)
                commands.submit {
                    val saved = repository.updateSettings(draft::applyTo)
                    dispatch(SettingsResult.Applied(draft, MeasurementDraft.from(saved)))
                }
            }
            is SettingsIntent.Day ->
                update { old ->
                    old.copy(
                        days =
                            old.days.mapIndexed { index, day ->
                                if (index == intent.index)
                                    if (day == SettingsDomModel.DAY_ENABLED)
                                        SettingsDomModel.DAY_DISABLED
                                    else SettingsDomModel.DAY_ENABLED
                                else day
                            }
                    )
                }
            is SettingsIntent.Time ->
                update {
                    if (intent.start) it.copy(start = intent.value) else it.copy(end = intent.value)
                }
            is SettingsIntent.Schedule ->
                if (intent.enabled) request(TrackingAction.SCHEDULE)
                else {
                    dispatch(SettingsResult.Access(null))
                    commands.submit {
                        control.setSchedule(false)
                        dispatch(SettingsResult.ClearError)
                    }
                }
            SettingsIntent.Start -> request(TrackingAction.MANUAL)
            SettingsIntent.Stop -> {
                dispatch(SettingsResult.Access(null))
                try {
                    control.stop()
                    dispatch(SettingsResult.ClearError)
                } catch (_: IllegalStateException) {
                    dispatch(SettingsResult.Error(R.string.tracking_stop_restricted))
                } catch (_: SecurityException) {
                    dispatch(SettingsResult.Error(R.string.tracking_stop_restricted))
                }
            }
            is SettingsIntent.AccessFinished -> {
                val action = state.value.accessRequest ?: return
                dispatch(SettingsResult.Access(null))
                if (intent.error != null) {
                    dispatch(SettingsResult.Error(intent.error))
                    return
                }
                if (control.capabilities().requirement() != TrackingRequirement.READY) {
                    dispatch(SettingsResult.Error(R.string.tracking_requirements_changed))
                    return
                }
                commands.submit {
                    if (action == TrackingAction.SCHEDULE) control.setSchedule(true)
                    else {
                        try {
                            control.start()
                        } catch (_: SecurityException) {
                            dispatch(SettingsResult.Error(R.string.tracking_permission_required))
                            return@submit
                        } catch (_: IllegalStateException) {
                            dispatch(SettingsResult.Error(R.string.tracking_start_restricted))
                            return@submit
                        }
                    }
                    dispatch(SettingsResult.ClearError)
                }
            }
            SettingsIntent.CancelAccess -> dispatch(SettingsResult.Access(null))
            SettingsIntent.Retry -> {
                dispatch(SettingsResult.ClearError)
                retry.value++
            }
            is SettingsIntent.Theme ->
                commands.submit {
                    repository.setThemeMode(intent.mode)
                    dispatch(SettingsResult.ClearError)
                }
            SettingsIntent.DismissError -> dispatch(SettingsResult.ClearError)
        }
    }

    private fun request(action: TrackingAction) {
        if (state.value.accessRequest != null) return
        val value = (state.value.settings as? LoadState.Ready)?.value ?: return
        if (
            action == TrackingAction.SCHEDULE &&
                (value.start == value.end || value.days.none { it == SettingsDomModel.DAY_ENABLED })
        ) {
            dispatch(SettingsResult.Error(R.string.tracking_invalid_schedule))
            return
        }
        dispatch(SettingsResult.Access(action))
    }

    private fun update(transform: (SettingsDomModel) -> SettingsDomModel) =
        commands.submit {
            val value =
                repository.updateSettings { current ->
                    transform(current).also {
                        require(
                            !it.isEnable ||
                                (it.start != it.end &&
                                    it.days.any { day -> day == SettingsDomModel.DAY_ENABLED })
                        )
                    }
                }
            if (value.isEnable) control.refreshSchedule()
            dispatch(SettingsResult.ClearError)
        }

    private fun dispatch(result: SettingsResult) {
        mutable.value = reduceSettings(mutable.value, result)
    }

    @Serializable
    private data class Snapshot(val draft: MeasurementDraft?, val action: TrackingAction?)

    private companion object {
        const val SETTINGS_STATE_KEY = "settings"
    }
}

@Serializable
enum class TrackingAction {
    MANUAL,
    SCHEDULE,
}

data class SettingsState(
    val settings: LoadState<SettingsDomModel> = LoadState.Loading,
    val draft: MeasurementDraft? = null,
    val saving: Boolean = false,
    val tracking: TrackingState = TrackingState.Stopped,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accessRequest: TrackingAction? = null,
    val error: Int? = null,
)

sealed interface SettingsIntent {
    data class Edit(val field: MeasurementField, val text: String) : SettingsIntent

    data object Apply : SettingsIntent

    data class Day(val index: Int) : SettingsIntent

    data class Time(val start: Boolean, val value: String) : SettingsIntent

    data class Schedule(val enabled: Boolean) : SettingsIntent

    data object Start : SettingsIntent

    data object Stop : SettingsIntent

    data class AccessFinished(val error: Int? = null) : SettingsIntent

    data object CancelAccess : SettingsIntent

    data object Retry : SettingsIntent

    data class Theme(val mode: ThemeMode) : SettingsIntent

    data object DismissError : SettingsIntent
}

internal sealed interface SettingsResult {
    data class Loaded(val value: LoadState<SettingsDomModel>) : SettingsResult

    data class Draft(val value: MeasurementDraft) : SettingsResult

    data object Saving : SettingsResult

    data class Applied(val submitted: MeasurementDraft, val saved: MeasurementDraft) :
        SettingsResult

    data class Tracking(val value: TrackingState) : SettingsResult

    data class Theme(val value: ThemeMode) : SettingsResult

    data class Access(val value: TrackingAction?) : SettingsResult

    data class Error(val message: Int) : SettingsResult

    data object ClearError : SettingsResult
}

internal fun reduceSettings(state: SettingsState, result: SettingsResult): SettingsState =
    when (result) {
        is SettingsResult.Loaded ->
            state.copy(
                settings = result.value,
                draft =
                    state.draft
                        ?: (result.value as? LoadState.Ready)?.value?.let(MeasurementDraft::from),
            )
        is SettingsResult.Draft -> state.copy(draft = result.value)
        SettingsResult.Saving -> state.copy(saving = true, error = null)
        is SettingsResult.Applied ->
            state.copy(
                saving = false,
                error = null,
                draft = if (state.draft == result.submitted) result.saved else state.draft,
            )
        is SettingsResult.Tracking -> state.copy(tracking = result.value)
        is SettingsResult.Theme -> state.copy(theme = result.value)
        is SettingsResult.Access -> state.copy(accessRequest = result.value)
        is SettingsResult.Error -> state.copy(error = result.message, saving = false)
        SettingsResult.ClearError -> state.copy(error = null)
    }
