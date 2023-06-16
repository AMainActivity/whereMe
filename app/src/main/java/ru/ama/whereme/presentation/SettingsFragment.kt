package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentSettingsBinding
import ru.ama.whereme.domain.entity.SettingsDomModel
import java.text.SimpleDateFormat
import javax.inject.Inject


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentSettingsBinding == null")
    private lateinit var viewModel: SettingsViewModel
    private var listOfCheckBox = listOf<AppCompatCheckBox>()
    private val component by lazy {
        (requireActivity().application as MyApp).component
    }
    private lateinit var workingTimeModel: SettingsDomModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    override fun onResume() {
        super.onResume()
        bindMyService()
        binding.frgmntSetSwitchStart.isChecked = viewModel.checkService()
    }

    private fun bindMyService() {
        requireActivity().bindService(
            MyForegroundService.newIntent(requireContext()),
            serviceConnection,
            0
        )
    }

    private fun bindUnbindService() {
        requireActivity().unbindService(serviceConnection)
        bindMyService()
    }

    private fun observeViewModel() {
        viewModel.errorMinDistance.observe(viewLifecycleOwner) {
            val message = if (it) {
                String.format(getString(R.string.set_format), MIN_DIST_LENGTH)
            } else {
                null
            }
            binding.frgmntSetMdEt.error = message
        }
        viewModel.errorAccuracy.observe(viewLifecycleOwner) {
            val message = if (it) {
                String.format(getString(R.string.set_format), ACCURACY_LENGTH)
            } else {
                null
            }
            binding.frgmntSetAccurEt.error = message
        }
        viewModel.errorTimeAccuracy.observe(viewLifecycleOwner) {
            val message = if (it) {
                String.format(getString(R.string.set_format), TIME_ACCURACY_LENGTH)
            } else {
                null
            }
            binding.frgmntSetTimeAcEt.error = message
        }
        viewModel.errorTimePeriod.observe(viewLifecycleOwner) {
            val message = if (it) {
                String.format(getString(R.string.set_format), TIME_PERIODIC_LENGTH)
            } else {
                null
            }
            binding.frgmntSetTimePovtorEt.error = message
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun getHourFromSet(id: Int): String {
        workingTimeModel = viewModel.getWorkingTime()
        val start = workingTimeModel.start.split(DELIMITER)
        val end = workingTimeModel.end.split(DELIMITER)
        var res = ""
        when (id) {
            1 -> res = start[0]
            2 -> res = start[1]
            3 -> res = end[0]
            4 -> res = end[1]
        }
        return res
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = "Настройки"
        viewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]
        setDays()
        workingTimeModel = viewModel.getWorkingTime()
        observeViewModel()
        binding.frgmntSetSwitchAc.isChecked = workingTimeModel.isEnable
        binding.frgmntSetSwitchStart.isChecked = viewModel.checkService()
        binding.frgmntSetSwitchStart.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {
                if (!viewModel.checkService()) {
                    ContextCompat.startForegroundService(
                        requireContext(),
                        MyForegroundService.newIntent(requireContext())
                    )
                    Log.e("frgmntSetSwitchStart", "isMyServiceRunning")
                }
            } else {
                if (viewModel.checkService()) {
                    Log.e("frgmntSetSwitchStart", "isMyServiceRunningFalse")
                    requireContext().stopService(MyForegroundService.newIntent(requireContext()))
                    viewModel.cancelAlarmService()
                }
            }
        }
        /*binding.frgmntSetSwitchStart.setOnClickListener { view ->
            bindUnbindService()
            if (!viewModel.checkService()) {
                ContextCompat.startForegroundService(
                    requireContext(),
                    MyForegroundService.newIntent(requireContext())
                )
                Log.e("frgmntSetSwitchStart", "isMyServiceRunning")
            } else {
                Log.e("frgmntSetSwitchStart", "isMyServiceRunningFalse")
                requireContext().stopService(MyForegroundService.newIntent(requireContext()))
                viewModel.cancelAlarmService()
            }
        }*/
        binding.frgmntSetSwitchAc.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.set_alarm_clock_set),
                    Toast.LENGTH_SHORT
                )
                    .show()
                viewModel.runAlarmClock()
                Log.e("frgmntSetSwitchAc", "будильник установлен")
            } else {
                Log.e("frgmntSetSwitchAc", "будильник отключен")
                viewModel.cancelAlarmClock()
            }
        })
        binding.frgmntSetButStart.setText(
            "${getHourFromSet(1)}:${getHourFromSet(2)}", null
        )
        binding.frgmntSetButEnd.setText(
            "${getHourFromSet(3)}:${getHourFromSet(4)}", null
        )
        binding.frgmntSetButStart.setOnClickListener {
            workingTimeModel = viewModel.getWorkingTime()
            var h = ""
            var m = ""
            val timePickerDialog =
                TimePickerDialog(requireContext(), { view, hourOfDay, minute ->
                    h =
                        if (hourOfDay.toString().length == 1) ZERO_STRING + (hourOfDay).toString() else (hourOfDay).toString()
                    m =
                        if (minute.toString().length == 1) ZERO_STRING + minute.toString() else minute.toString()
                    Log.e("Time", "$h:$m")
                    if (!compare2Times("$h:$m", workingTimeModel.end))
                        Toast.makeText(
                            requireContext(),
                            String.format(getString(R.string.set_format_end, workingTimeModel.end)),
                            Toast.LENGTH_SHORT
                        ).show()
                    else {
                        viewModel.setWorkingTime(
                            workingTimeModel.copy(
                                start = "$h:$m",
                                isEnable = true
                            )
                        )
                        if (binding.frgmntSetSwitchAc.isChecked)
                            viewModel.runAlarmClock() else
                            binding.frgmntSetSwitchAc.isChecked = true
                        binding.frgmntSetButStart.setText(
                            "$h:$m", null
                        )
                    }
                }, getHourFromSet(1).toInt(), getHourFromSet(2).toInt(), true)
            timePickerDialog.show()
        }
        binding.frgmntSetButEnd.setOnClickListener {
            workingTimeModel = viewModel.getWorkingTime()
            var h = ""
            var m = ""
            val timePickerDialog =
                TimePickerDialog(
                    requireContext(),
                    { view, hourOfDay, minute ->
                        h =
                            if (hourOfDay.toString().length == 1) ZERO_STRING + (hourOfDay).toString() else (hourOfDay).toString()
                        m =
                            if (minute.toString().length == 1) ZERO_STRING + minute.toString() else minute.toString()
                        Log.e("endTime", "$h:$m")
                        if (!compare2Times(workingTimeModel.start, "$h:$m"))
                            Toast.makeText(
                                requireContext(),
                                String.format(
                                    getString(
                                        R.string.set_format_start,
                                        workingTimeModel.start
                                    )
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        else {
                            viewModel.setWorkingTime(
                                workingTimeModel.copy(
                                    end = "$h:$m",
                                    isEnable = true
                                )
                            )
                            if (binding.frgmntSetSwitchAc.isChecked)
                                viewModel.runAlarmClock() else
                                binding.frgmntSetSwitchAc.isChecked = true
                            binding.frgmntSetButEnd.setText(
                                "$h:$m", null
                            )
                        }
                    },
                    getHourFromSet(3).toInt(),
                    getHourFromSet(4).toInt(),
                    true
                )
            timePickerDialog.show()
        }
        setOtherSettings()
        binding.frgmntSetMdEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.resetError(SettingsViewNames.MIN_DISTANCE)
            }

            override fun afterTextChanged(s: Editable) {
                viewModel.validateInputData(s.toString(), SettingsViewNames.MIN_DISTANCE)
            }
        })
        binding.frgmntSetAccurEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.resetError(SettingsViewNames.ACCURACY)
            }

            override fun afterTextChanged(s: Editable) {
                viewModel.validateInputData(s.toString(), SettingsViewNames.ACCURACY)
            }
        })
        binding.frgmntSetTimeAcEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.resetError(SettingsViewNames.TIME_ACCURACY)
            }

            override fun afterTextChanged(s: Editable) {
                viewModel.validateInputData(s.toString(), SettingsViewNames.TIME_ACCURACY)
            }
        })
        binding.frgmntSetTimePovtorEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.resetError(SettingsViewNames.TIME_PERIOD)
            }

            override fun afterTextChanged(s: Editable) {
                viewModel.validateInputData(s.toString(), SettingsViewNames.TIME_PERIOD)
            }
        })
    }

    private fun setOtherSettings() {
        binding.frgmntSetAccurEt.setText(workingTimeModel.accuracy.toString())
        (binding.frgmntSetMdEt).setText(workingTimeModel.minDist.toString())
        (binding.frgmntSetTimeAcEt).setText(workingTimeModel.timeOfWaitAccuracy.toString())
        (binding.frgmntSetTimePovtorEt).setText(workingTimeModel.timeOfWorkingWM.toString())
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = (service as? MyForegroundService.LocalBinder) ?: return
            val foregroundService = binder.getService()
            foregroundService.isServiseAlive = { flag ->
                try {
                    binding.frgmntSetSwitchStart.isChecked = flag
                } catch (e: Exception) {
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    private fun compare2Times(start: String, end: String): Boolean {
        var res = false
        val sdf = SimpleDateFormat("HH:mm")
        val strDate = sdf.parse(start)
        val endDate = sdf.parse(end)
        if (endDate.time > strDate.time) {
            res = true
        }
        Log.e("compare2Times", "$strDate ### $endDate %%% $res")
        return res
    }

    private fun setDays() {
        workingTimeModel = viewModel.getWorkingTime()
        val listOfDays = workingTimeModel.days
        listOfCheckBox = listOf(
            binding.frgmntSetCbD1,
            binding.frgmntSetCbD2,
            binding.frgmntSetCbD3,
            binding.frgmntSetCbD4,
            binding.frgmntSetCbD5,
            binding.frgmntSetCbD6,
            binding.frgmntSetCbD7
        )
        for (cb in listOfCheckBox) {
            cb.setOnCheckedChangeListener { buttonView, isChecked ->
                saveSettings()
            }
        }
        if (listOfDays.size == listOfCheckBox.size) {
            for (i in listOfDays.indices) {
                listOfCheckBox[i].isChecked = listOfDays[i] == ONE_UNIT
            }
        }
    }

    private fun saveSettings() {
        val listOfDays1: MutableList<String> = mutableListOf<String>()
        for (cb in listOfCheckBox) {
            listOfDays1.add((cb.isChecked).toIntTxt())
        }
        workingTimeModel = viewModel.getWorkingTime()
        viewModel.setWorkingTime(workingTimeModel.copy(days = listOfDays1))
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unbindService(serviceConnection)
    }

    private fun Boolean.toIntTxt() = if (this) ONE_UNIT else ZERO_STRING

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val MIN_DIST_LENGTH = 10
        private const val ACCURACY_LENGTH = 50
        private const val TIME_ACCURACY_LENGTH = 20
        private const val TIME_PERIODIC_LENGTH = 15
        private const val DELIMITER = ":"
        private const val ONE_UNIT = "1"
        private const val ZERO_STRING = "0"
        private const val ZERO_INT = 0
        private const val EMPTY_STRING = ""
    }
}