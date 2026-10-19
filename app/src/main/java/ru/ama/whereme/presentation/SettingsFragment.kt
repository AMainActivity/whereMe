package ru.ama.whereme.presentation

import android.app.TimePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import ru.ama.whereme.tracking.*
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.tracking.TrackingState
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentSettingsBinding
import ru.ama.whereme.domain.entity.SettingsDomModel
import java.util.Locale

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SettingsViewModel
    private var rendering = false
    private var renderedSettings: SettingsDomModel? = null
    private var timeDialog: TimePickerDialog? = null
    private var accessDialog: AlertDialog? = null
    private val foregroundPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.waitingForAccessResult = false
        if (viewModel.pendingTrackingAction == null) return@registerForActivityResult
        val requirement = requireContext().trackingCapabilities().requirement()
        if (requirement == TrackingRequirement.FOREGROUND_LOCATION || requirement == TrackingRequirement.PRECISE_LOCATION) {
            accessDenied(R.string.tracking_permission_required)
        } else continueTrackingRequest()
    }
    private val backgroundPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.waitingForAccessResult = false
        if (granted) continueTrackingRequest() else accessDenied(R.string.tracking_background_required)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.waitingForAccessResult = false
        // Notification denial does not revoke location access or prevent an allowed foreground service.
        completeTrackingRequest()
    }
    private val locationSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.waitingForAccessResult = false
        if (requireContext().trackingCapabilities().locationEnabled) continueTrackingRequest()
        else accessDenied(R.string.tracking_location_disabled)
    }
    private val backgroundSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.waitingForAccessResult = false
        if (requireContext().trackingCapabilities().background) continueTrackingRequest()
        else accessDenied(R.string.tracking_background_required)
    }
    private val alarmSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.waitingForAccessResult = false
        if (requireContext().trackingCapabilities().exactAlarm) continueTrackingRequest()
        else accessDenied(R.string.tracking_alarm_required)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.setSubtitle(R.string.menu_settings)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        val b = binding
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tracking.collect { state ->
                    showServiceState(state.isRunning)
                    renderTrackingState(state)
                }
            }
        }
        days().forEachIndexed { index, checkBox ->
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (!rendering) viewModel.update { current ->
                    current.copy(days = current.days.mapIndexed { i, day -> if (i == index) if (checked) "1" else "0" else day })
                }
            }
        }
        b.frgmntSetSwitchAc.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) {
                if (enabled) requestTracking(TrackingAction.SCHEDULE)
                else { viewModel.cancelTrackingRequest(); viewModel.setSchedule(false) }
            }
        }
        b.frgmntSetSwitchStart.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) {
                if (enabled) requestTracking(TrackingAction.MANUAL)
                else {
                    viewModel.cancelTrackingRequest()
                    try { requireContext().startService(MyForegroundService.stopIntent(requireContext()))
                    } catch (_: IllegalStateException) { accessDenied(R.string.tracking_stop_restricted)
                    } catch (_: SecurityException) { accessDenied(R.string.tracking_stop_restricted) }
                }
            }
        }
        b.frgmntSetButStart.setOnClickListener { pickTime(true) }
        b.frgmntSetButEnd.setOnClickListener { pickTime(false) }
        number(b.frgmntSetMdEt, MeasurementField.DISTANCE)
        number(b.frgmntSetAccurEt, MeasurementField.ACCURACY)
        number(b.frgmntSetTimeAcEt, MeasurementField.WAIT)
        number(b.frgmntSetTimePovtorEt, MeasurementField.PERIOD)
        b.applyMeasurements.setOnClickListener { viewModel.applyMeasurements() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.draft.collect { renderMeasurements() } }
                launch { viewModel.isSaving.collect { renderMeasurements() } }
                launch {
                    viewModel.settings.collect { state ->
                        when (state) {
                            is LoadState.Ready -> { enableSettings(true); render(state.value); renderMeasurements(); renderTrackingState(viewModel.tracking.value) }
                            LoadState.Loading -> enableSettings(false)
                            LoadState.Failed -> {
                                enableSettings(false)
                                Snackbar.make(b.root, R.string.storage_read_failed, Snackbar.LENGTH_INDEFINITE)
                                    .setAction(R.string.storage_retry) { viewModel.retrySettings() }.show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        binding.operationError.isVisible = error != null
                        if (error != null) {
                            binding.operationError.setText(error)
                            renderedSettings?.let(::render)
                        }
                    }
                }
            }
        }
    }

    private fun renderTrackingState(state: TrackingState) {
        val scheduleEnabled = (viewModel.settings.value as? LoadState.Ready)?.value?.isEnable == true
        if ((state == TrackingState.Stopped && scheduleEnabled) || state is TrackingState.Armed) {
            val message = when (requireContext().trackingCapabilities().requirement()) {
                TrackingRequirement.FOREGROUND_LOCATION, TrackingRequirement.PRECISE_LOCATION -> R.string.tracking_permission_required
                TrackingRequirement.LOCATION_ENABLED -> R.string.tracking_location_disabled
                TrackingRequirement.PROVIDER -> R.string.tracking_no_provider
                TrackingRequirement.BACKGROUND_LOCATION -> R.string.tracking_background_required
                TrackingRequirement.EXACT_ALARM -> R.string.tracking_alarm_required
                TrackingRequirement.READY -> if (state is TrackingState.Armed) R.string.tracking_armed else R.string.tracking_schedule_ready
            }
            binding.trackingStatus.setText(message)
            return
        }
        binding.trackingStatus.setText(when (state) {
            is TrackingState.Starting -> R.string.tracking_starting
            is TrackingState.Armed -> R.string.tracking_armed
            TrackingState.Stopped -> R.string.tracking_stopped
            is TrackingState.Collecting -> R.string.tracking_collecting
            is TrackingState.Saving -> R.string.tracking_saving
            is TrackingState.Uploading -> R.string.tracking_uploading
            TrackingState.Stopping -> R.string.tracking_stopping
            is TrackingState.Waiting -> when {
                state.failure != null -> R.string.tracking_sample_failed
                state.syncFailed -> R.string.tracking_sync_failed
                state.noFix -> R.string.tracking_no_fix
                else -> R.string.tracking_waiting
            }
            is TrackingState.Failed -> if (state.reason == ru.ama.whereme.domain.tracking.TrackingFailure.ALARM)
                R.string.tracking_alarm_required else R.string.tracking_failed
            is TrackingState.LocationUnavailable -> when (state.reason) {
                LocationOutcome.Reason.PERMISSION_REQUIRED -> R.string.tracking_permission_required
                LocationOutcome.Reason.LOCATION_DISABLED -> R.string.tracking_location_disabled
                LocationOutcome.Reason.NO_PROVIDER -> R.string.tracking_no_provider
                LocationOutcome.Reason.NO_FIX -> R.string.tracking_no_fix
            }
        })
    }

    private fun days() = binding.run { listOf(frgmntSetCbD1, frgmntSetCbD2, frgmntSetCbD3,
        frgmntSetCbD4, frgmntSetCbD5, frgmntSetCbD6, frgmntSetCbD7) }

    private fun enableSettings(enabled: Boolean) {
        (days() + binding.run { listOf(frgmntSetSwitchAc, frgmntSetButStart, frgmntSetButEnd,
            frgmntSetMdEt, frgmntSetAccurEt, frgmntSetTimeAcEt, frgmntSetTimePovtorEt) })
            .forEach { it.isEnabled = enabled }
        binding.frgmntSetSwitchStart.isEnabled = enabled || viewModel.tracking.value.isRunning
    }

    private fun render(value: SettingsDomModel) {
        rendering = true
        try {
            days().forEachIndexed { index, checkBox -> checkBox.isChecked = value.days[index] == "1" }
            binding.frgmntSetSwitchAc.isChecked = value.isEnable
            binding.frgmntSetButStart.setText(value.start)
            binding.frgmntSetButEnd.setText(value.end)
            renderedSettings = value
        } finally { rendering = false }
    }

    private fun number(view: EditText, field: MeasurementField) {
        view.doAfterTextChanged { text ->
            if (!rendering) viewModel.editMeasurement(field, text.toString())
        }
    }

    private fun renderMeasurements() {
        val draft = viewModel.draft.value ?: return
        rendering = true
        try {
            val fields = listOf(binding.frgmntSetMdEt, binding.frgmntSetAccurEt,
                binding.frgmntSetTimeAcEt, binding.frgmntSetTimePovtorEt)
            MeasurementField.entries.zip(fields).forEach { (field, view) ->
                val text = draft.value(field)
                if (view.text.toString() != text) view.setText(text)
                view.error = if (draft.isValid(field)) null else getString(R.string.set_format,
                    MeasurementDraft.minimum(field))
            }
            val stored = (viewModel.settings.value as? LoadState.Ready)?.value
            binding.applyMeasurements.isEnabled = stored != null && draft.isValid &&
                draft != MeasurementDraft.from(stored) && !viewModel.isSaving.value
        } finally { rendering = false }
    }

    private fun pickTime(start: Boolean) {
        val current = renderedSettings ?: return
        val parts = (if (start) current.start else current.end).split(':')
        timeDialog?.dismiss()
        timeDialog = TimePickerDialog(requireContext(), { _, hour, minute ->
            val value = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
            viewModel.update { if (start) it.copy(start = value) else it.copy(end = value) }
        }, parts[0].toInt(), parts[1].toInt(), true).also { it.show() }
    }

    override fun onResume() {
        super.onResume()
        showServiceState(viewModel.tracking.value.isRunning)
        renderTrackingState(viewModel.tracking.value)
        if (viewModel.pendingTrackingAction != null && !viewModel.waitingForAccessResult) continueTrackingRequest()
    }

    private fun requestTracking(action: TrackingAction) {
        if (viewModel.pendingTrackingAction != null) return
        if (action == TrackingAction.SCHEDULE && renderedSettings?.let { it.start == it.end || it.days.none { day -> day == "1" } } != false) {
            renderedSettings?.let(::render)
            Snackbar.make(binding.root, R.string.tracking_invalid_schedule, Snackbar.LENGTH_LONG).show()
            return
        }
        viewModel.pendingTrackingAction = action
        renderedSettings?.let(::render)
        showServiceState(viewModel.tracking.value.isRunning)
        continueTrackingRequest()
    }

    private fun continueTrackingRequest() {
        if (viewModel.pendingTrackingAction == null) return
        if (viewModel.waitingForAccessResult || accessDialog?.isShowing == true) return
        when (requireContext().trackingCapabilities().requirement()) {
            TrackingRequirement.FOREGROUND_LOCATION, TrackingRequirement.PRECISE_LOCATION -> explain(R.string.tracking_location_explanation) {
                viewModel.waitingForAccessResult = true
                foregroundPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            TrackingRequirement.LOCATION_ENABLED -> explain(R.string.tracking_location_disabled) {
                viewModel.waitingForAccessResult = true
                locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            TrackingRequirement.PROVIDER -> accessDenied(R.string.tracking_no_provider)
            TrackingRequirement.BACKGROUND_LOCATION -> explain(R.string.tracking_background_explanation) {
                viewModel.waitingForAccessResult = true
                if (Build.VERSION.SDK_INT >= 30) backgroundSettings.launch(applicationSettingsIntent())
                else if (Build.VERSION.SDK_INT >= 29) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else accessDenied(R.string.tracking_requirements_changed)
            }
            TrackingRequirement.EXACT_ALARM -> explain(R.string.tracking_alarm_explanation) {
                viewModel.waitingForAccessResult = true
                if (Build.VERSION.SDK_INT >= 31) alarmSettings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    ("package:" + requireContext().packageName).toUri()))
                else accessDenied(R.string.tracking_requirements_changed)
            }
            TrackingRequirement.READY -> {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    viewModel.waitingForAccessResult = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else completeTrackingRequest()
            }
        }
    }

    private fun explain(message: Int, action: () -> Unit) {
        accessDialog = AlertDialog.Builder(requireContext()).setMessage(message)
            .setPositiveButton(R.string.tracking_continue) { dialog, _ -> dialog.dismiss(); accessDialog = null; action() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> viewModel.cancelTrackingRequest() }
            .setOnCancelListener { viewModel.cancelTrackingRequest() }.create()
        accessDialog?.show()
    }

    private fun completeTrackingRequest() {
        val action = viewModel.pendingTrackingAction ?: return
        viewModel.cancelTrackingRequest()
        if (requireContext().trackingCapabilities().requirement() != TrackingRequirement.READY) {
            accessDenied(R.string.tracking_requirements_changed)
            return
        }
        try {
            if (action == TrackingAction.SCHEDULE) viewModel.setSchedule(true)
            else ContextCompat.startForegroundService(requireContext(), MyForegroundService.newIntent(requireContext()))
        } catch (_: SecurityException) { accessDenied(R.string.tracking_permission_required)
        } catch (_: IllegalStateException) { accessDenied(R.string.tracking_start_restricted) }
    }

    private fun accessDenied(message: Int) {
        viewModel.cancelTrackingRequest()
        showServiceState(viewModel.tracking.value.isRunning)
        renderedSettings?.let(::render)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.tracking_app_settings) { startActivity(applicationSettingsIntent()) }.show()
    }

    private fun applicationSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        ("package:" + requireContext().packageName).toUri())

    private fun showServiceState(running: Boolean) {
        rendering = true
        try {
            _binding?.frgmntSetSwitchStart?.let {
                it.isChecked = running
                it.isEnabled = running || viewModel.settings.value is LoadState.Ready
            }
        } finally { rendering = false }
    }

    override fun onDestroyView() {
        timeDialog?.dismiss()
        timeDialog = null
        accessDialog?.setOnCancelListener(null)
        accessDialog?.dismiss()
        accessDialog = null
        renderedSettings = null
        _binding = null
        super.onDestroyView()
    }
}
