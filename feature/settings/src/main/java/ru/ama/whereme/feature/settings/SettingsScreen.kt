package ru.ama.whereme.feature.settings

import java.time.LocalTime
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.ThemeMode
import ru.ama.whereme.domain.state.LoadState
import ru.ama.whereme.domain.tracking.*

@Composable
fun SettingsScreen(component: SettingsComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()
    var requirement by remember { mutableStateOf(component.capabilities().requirement()) }
    LifecycleResumeEffect(component, state.tracking) {
        requirement = component.capabilities().requirement()
        onPauseOrDispose {}
    }
    SettingsContent(state, component::accept, modifier, requirement)
    TrackingAccessRequest(component, state.accessRequest)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    requirement: TrackingRequirement = TrackingRequirement.READY,
) {
    val context = LocalContext.current
    val scheduleLabel = stringResource(R.string.settings_schedule)
    var systemSettingsFailed by remember { mutableStateOf(false) }
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val settings = (state.settings as? LoadState.Ready)?.value
    val canToggleRecording =
        state.tracking != TrackingState.Stopping &&
            state.accessRequest == null &&
            (state.tracking.isRunning || settings != null)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = state.tracking.isRunning,
                            enabled = canToggleRecording,
                            role = Role.Switch,
                            onValueChange = {
                                onIntent(if (it) SettingsIntent.Start else SettingsIntent.Stop)
                            },
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_recording),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(
                        checked = state.tracking.isRunning,
                        onCheckedChange = null,
                        enabled = canToggleRecording,
                    )
                }
                Text(
                    stringResource(
                        if (
                            (state.tracking is TrackingState.Armed || settings?.isEnable == true) &&
                                requirement != TrackingRequirement.READY
                        )
                            requirementText(requirement)
                        else trackingText(state.tracking, settings?.isEnable == true)
                    )
                )
            }
        }
        state.error?.let { message ->
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(message))
                    if (
                        message in
                            listOf(
                                R.string.settings_access_failed,
                                R.string.tracking_requirements_changed,
                                R.string.tracking_no_provider,
                                R.string.tracking_permission_required,
                            )
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            "package:${context.packageName}".toUri(),
                                        )
                                    )
                                    systemSettingsFailed = false
                                } catch (_: ActivityNotFoundException) {
                                    systemSettingsFailed = true
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_open_system))
                        }
                        if (systemSettingsFailed)
                            Text(stringResource(R.string.settings_no_activity))
                    }
                    if (message == R.string.settings_theme_error) {
                        TextButton(onClick = { onIntent(SettingsIntent.Retry) }) {
                            Text(stringResource(R.string.settings_retry))
                        }
                    }
                    TextButton(onClick = { onIntent(SettingsIntent.DismissError) }) {
                        Text(stringResource(R.string.settings_dismiss))
                    }
                }
            }
        }
        when (state.settings) {
            LoadState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_loading))
            }
            LoadState.Failed -> {
                Text(
                    stringResource(R.string.settings_read_error),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { onIntent(SettingsIntent.Retry) }) {
                    Text(stringResource(R.string.settings_retry))
                }
            }
            is LoadState.Ready -> Unit
        }
        if (settings != null) {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_schedule),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Switch(
                        modifier = Modifier.semantics { contentDescription = scheduleLabel },
                        checked = settings.isEnable,
                        onCheckedChange = { onIntent(SettingsIntent.Schedule(it)) },
                    )
                }
                Text(
                    stringResource(R.string.settings_schedule_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stringArrayResource(R.array.settings_days).forEachIndexed { index, label ->
                        FilterChip(
                            selected = settings.days[index] == SettingsDomModel.DAY_ENABLED,
                            onClick = { onIntent(SettingsIntent.Day(index)) },
                            label = { Text(label) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editingStart = true }) {
                        Text(stringResource(R.string.settings_start_time, settings.start))
                    }
                    OutlinedButton(onClick = { editingStart = false }) {
                        Text(stringResource(R.string.settings_end_time, settings.end))
                    }
                }
            }
            SettingsCard {
                Text(
                    stringResource(R.string.settings_measurements),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.settings_measurements_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.draft?.let { draft ->
                    MeasurementField.entries.forEach { field ->
                        OutlinedTextField(
                            value = draft.value(field),
                            onValueChange = { onIntent(SettingsIntent.Edit(field, it)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text(
                                    stringResource(
                                        when (field) {
                                            MeasurementField.DISTANCE -> R.string.settings_distance
                                            MeasurementField.ACCURACY -> R.string.settings_accuracy
                                            MeasurementField.WAIT -> R.string.settings_wait
                                            MeasurementField.PERIOD -> R.string.settings_period
                                        }
                                    )
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !draft.isValid(field),
                            supportingText = {
                                if (!draft.isValid(field))
                                    Text(
                                        stringResource(
                                            R.string.settings_minimum,
                                            MeasurementDraft.minimum(field),
                                        )
                                    )
                            },
                        )
                    }
                    Button(
                        onClick = { onIntent(SettingsIntent.Apply) },
                        enabled =
                            draft.isValid &&
                                !state.saving &&
                                draft != MeasurementDraft.from(settings),
                    ) {
                        Text(
                            stringResource(
                                if (state.saving) R.string.settings_saving
                                else R.string.settings_apply
                            )
                        )
                    }
                }
            }
        }
        SettingsCard {
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleLarge,
            )
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = state.theme == mode,
                            role = Role.RadioButton,
                            onClick = { onIntent(SettingsIntent.Theme(mode)) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.theme == mode, onClick = null)
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_system
                                ThemeMode.LIGHT -> R.string.settings_light
                                ThemeMode.DARK -> R.string.settings_dark
                            }
                        ),
                        Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
    val start = editingStart
    if (start != null && settings != null) {
        key(start) {
            RecordingTimeDialog(
                value = if (start) settings.start else settings.end,
                title =
                    stringResource(
                        if (start) R.string.settings_time_start_title
                        else R.string.settings_time_end_title
                    ),
                onDismiss = { editingStart = null },
                onConfirm = {
                    onIntent(SettingsIntent.Time(start, it))
                    editingStart = null
                },
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingTimeDialog(
    value: LocalTime,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val picker = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
    var keyboard by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = TimeDialogMaxWidth).imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.width(IntrinsicSize.Min)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (keyboard) TimeInput(picker) else TimePicker(picker)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { keyboard = !keyboard }) {
                        Icon(
                            painterResource(
                                if (keyboard) R.drawable.ic_clock else R.drawable.ic_keyboard
                            ),
                            stringResource(
                                if (keyboard) R.string.settings_time_dial
                                else R.string.settings_time_keyboard
                            ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(
                                LocalTime.of(picker.hour, picker.minute)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_done))
                    }
                }
            }
        }
    }
}

private fun trackingText(state: TrackingState, scheduled: Boolean): Int =
    when (state) {
        TrackingState.Stopped ->
            if (scheduled) R.string.tracking_schedule_ready else R.string.tracking_stopped
        is TrackingState.Armed -> R.string.tracking_armed
        is TrackingState.Starting -> R.string.tracking_starting
        is TrackingState.Collecting -> R.string.tracking_collecting
        is TrackingState.Saving -> R.string.tracking_saving
        is TrackingState.Uploading -> R.string.tracking_uploading
        TrackingState.Stopping -> R.string.tracking_stopping
        is TrackingState.Waiting ->
            when {
                state.failure != null -> R.string.tracking_sample_failed
                state.syncFailed -> R.string.tracking_sync_failed
                state.noFix -> R.string.tracking_no_fix
                else -> R.string.tracking_waiting
            }
        is TrackingState.Failed ->
            if (state.reason == TrackingFailure.ALARM) R.string.tracking_alarm_required
            else R.string.tracking_failed
        is TrackingState.LocationUnavailable ->
            when (state.reason) {
                LocationOutcome.Reason.PERMISSION_REQUIRED -> R.string.tracking_permission_required
                LocationOutcome.Reason.LOCATION_DISABLED -> R.string.tracking_location_disabled
                LocationOutcome.Reason.NO_PROVIDER -> R.string.tracking_no_provider
                LocationOutcome.Reason.NO_FIX -> R.string.tracking_no_fix
            }
    }

private fun requirementText(requirement: TrackingRequirement): Int =
    when (requirement) {
        TrackingRequirement.FOREGROUND_LOCATION,
        TrackingRequirement.PRECISE_LOCATION -> R.string.tracking_permission_required
        TrackingRequirement.LOCATION_ENABLED -> R.string.tracking_location_disabled
        TrackingRequirement.PROVIDER -> R.string.tracking_no_provider
        TrackingRequirement.BACKGROUND_LOCATION -> R.string.tracking_background_required
        TrackingRequirement.EXACT_ALARM -> R.string.tracking_alarm_required
        TrackingRequirement.READY -> R.string.tracking_armed
    }

private val TimeDialogMaxWidth = 560.dp
