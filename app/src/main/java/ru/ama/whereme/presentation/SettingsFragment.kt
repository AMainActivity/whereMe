package ru.ama.whereme.presentation

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import androidx.lifecycle.asLiveData
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
    private var serviceBound = false
    private var boundService: MyForegroundService? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.setSubtitle(R.string.menu_settings)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        val b = binding
        days().forEachIndexed { index, checkBox ->
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (!rendering) viewModel.update { current ->
                    current.copy(days = current.days.mapIndexed { i, day -> if (i == index) if (checked) "1" else "0" else day })
                }
            }
        }
        b.frgmntSetSwitchAc.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) viewModel.setSchedule(enabled)
        }
        b.frgmntSetSwitchStart.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) changeService(enabled)
        }
        b.frgmntSetButStart.setOnClickListener { pickTime(true) }
        b.frgmntSetButEnd.setOnClickListener { pickTime(false) }
        number(b.frgmntSetMdEt, 10) { s, value -> s.copy(minDist = value) }
        number(b.frgmntSetAccurEt, 50) { s, value -> s.copy(accuracy = value) }
        number(b.frgmntSetTimeAcEt, 20) { s, value -> s.copy(timeOfWaitAccuracy = value) }
        number(b.frgmntSetTimePovtorEt, 15) { s, value -> s.copy(timeOfWorkingWM = value) }
        viewModel.settings.asLiveData().observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Ready -> { enableSettings(true); render(state.value) }
                LoadState.Loading -> enableSettings(false)
                LoadState.Failed -> {
                    enableSettings(false)
                    Snackbar.make(b.root, R.string.storage_read_failed, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.storage_retry) { viewModel.retrySettings() }.show()
                }
            }
        }
        viewModel.errors.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            renderedSettings?.let(::render)
        }
    }

    private fun days() = binding.run { listOf(frgmntSetCbD1, frgmntSetCbD2, frgmntSetCbD3,
        frgmntSetCbD4, frgmntSetCbD5, frgmntSetCbD6, frgmntSetCbD7) }

    private fun enableSettings(enabled: Boolean) {
        (days() + binding.run { listOf(frgmntSetSwitchAc, frgmntSetButStart, frgmntSetButEnd,
            frgmntSetMdEt, frgmntSetAccurEt, frgmntSetTimeAcEt, frgmntSetTimePovtorEt) })
            .forEach { it.isEnabled = enabled }
        binding.frgmntSetSwitchStart.isEnabled = enabled || boundService?.isTracking == true
    }

    private fun render(value: SettingsDomModel) {
        val first = renderedSettings == null
        rendering = true
        try {
            days().forEachIndexed { index, checkBox -> checkBox.isChecked = value.days[index] == "1" }
            binding.frgmntSetSwitchAc.isChecked = value.isEnable
            binding.frgmntSetButStart.setText(value.start)
            binding.frgmntSetButEnd.setText(value.end)
            fun field(view: EditText, number: Int) {
                if ((first || !view.hasFocus()) && view.text.toString() != number.toString()) view.setText(number.toString())
            }
            field(binding.frgmntSetMdEt, value.minDist)
            field(binding.frgmntSetAccurEt, value.accuracy)
            field(binding.frgmntSetTimeAcEt, value.timeOfWaitAccuracy)
            field(binding.frgmntSetTimePovtorEt, value.timeOfWorkingWM)
            renderedSettings = value
        } finally { rendering = false }
    }

    private fun number(view: EditText, minimum: Int, change: (SettingsDomModel, Int) -> SettingsDomModel) {
        view.doAfterTextChanged { text ->
            if (!rendering) {
                val value = text.toString().toIntOrNull()
                if (value == null || value < minimum) view.error = getString(R.string.set_format, minimum)
                else { view.error = null; viewModel.update { change(it, value) } }
            }
        }
    }

    private fun pickTime(start: Boolean) {
        val current = renderedSettings ?: return
        val parts = (if (start) current.start else current.end).split(':')
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val value = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
            val latest = renderedSettings ?: return@TimePickerDialog
            if ((start && value >= latest.end) || (!start && value <= latest.start)) {
                Toast.makeText(requireContext(), if (start) getString(R.string.set_format_end, latest.end)
                    else getString(R.string.set_format_start, latest.start), Toast.LENGTH_SHORT).show()
            } else viewModel.update { if (start) it.copy(start = value) else it.copy(end = value) }
        }, parts[0].toInt(), parts[1].toInt(), true).show()
    }

    override fun onResume() {
        super.onResume()
        if (!serviceBound) serviceBound = requireActivity().bindService(
            MyForegroundService.newIntent(requireContext()), serviceConnection, 0)
        showServiceState(boundService?.isTracking ?: viewModel.checkService())
    }

    private fun changeService(enabled: Boolean) {
        try {
            if (enabled) ContextCompat.startForegroundService(requireContext(), MyForegroundService.newIntent(requireContext()))
            else {
                val service = boundService
                if (service != null) service.stopTracking()
                else if (viewModel.checkService()) requireContext().startService(MyForegroundService.stopIntent(requireContext()))
                viewModel.cancelAlarmService()
                unbindService()
            }
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.ma_no_access, Toast.LENGTH_LONG).show()
            showServiceState(false)
        } catch (_: IllegalStateException) { showServiceState(false) }
    }

    private fun showServiceState(running: Boolean) {
        rendering = true
        try {
            _binding?.frgmntSetSwitchStart?.let {
                it.isChecked = running
                it.isEnabled = running || viewModel.settings.value is LoadState.Ready
            }
        } finally { rendering = false }
    }
    private fun unbindService() {
        boundService?.isServiseAlive = null
        boundService = null
        if (serviceBound) { requireActivity().unbindService(serviceConnection); serviceBound = false }
    }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val foreground = (service as? MyForegroundService.LocalBinder)?.getService() ?: return
            boundService = foreground
            showServiceState(foreground.isTracking)
            foreground.isServiseAlive = ::showServiceState
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundService = null; showServiceState(false) }
    }
    override fun onStop() { unbindService(); super.onStop() }
    override fun onDestroyView() {
        unbindService()
        renderedSettings = null
        _binding = null
        super.onDestroyView()
    }
}
