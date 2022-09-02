package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
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
import ru.ama.whereme.data.database.SettingsDomModel
import ru.ama.whereme.databinding.FragmentSettingsBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }


    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_set_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.menu_set_frgmnt -> {
                saveSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setActionBarSubTitle(txt: String) {
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = txt
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root

    }

    fun getHourFromSet(id: Int): String {
        workingTimeModel = viewModel.getWorkingTime()
        val start = workingTimeModel.start.split(":")
        val end = workingTimeModel.end.split(":")
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


        viewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]
        setDays()
        binding.frgmntSetSwitchStart.isChecked = viewModel.сheckService()
        binding.frgmntSetSwitchAc.isChecked = workingTimeModel.isEnable
        binding.frgmntSetSwitchStart.setOnClickListener { view ->
            if (!viewModel.сheckService()) {
              //  if (viewModel.isTimeToGetLocaton())
                    ContextCompat.startForegroundService(
                        requireContext(),
                        MyForegroundService.newIntent(requireContext())
                    )
               /* else {
                    Toast.makeText(requireContext(), "будильник установлен", Toast.LENGTH_SHORT)
                        .show()
                    viewModel.runAlarmClock()
                }*/
                Log.e("frgmntSetSwitchStart", "isMyServiceRunning")
            } else {
                Log.e("frgmntSetSwitchStart", "isMyServiceRunningFalse")
                requireContext().stopService(MyForegroundService.newIntent(requireContext()))

            }
        }
        binding.frgmntSetSwitchAc.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                Toast.makeText(requireContext(), "будильник установлен", Toast.LENGTH_SHORT)
                    .show()
                viewModel.runAlarmClock()
                Log.e("frgmntSetSwitchAc", "будильник установлен")
            } else {
                Log.e("frgmntSetSwitchAc", "будильник отключен")
                viewModel.cancelAlarmClock()
                requireContext().stopService(MyForegroundService.newIntent(requireContext()))

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
                        if (hourOfDay.toString().length == 1) "0" + (hourOfDay).toString() else (hourOfDay).toString()
                    m =
                        if (minute.toString().length == 1) "0" + minute.toString() else minute.toString()
                    Log.e("Time", "$h:$m")
                    if (!compare2Times("$h:$m", workingTimeModel.end))
                        Toast.makeText(
                            requireContext(),
                            "время должо быть раньше времени конца: ${workingTimeModel.end}",
                            Toast.LENGTH_SHORT
                        ).show()
                    else {
                        binding.frgmntSetButStart.setText(
                            "$h:$m", null
                        )
                    }
                    //     viewModel.setWorkingTime(workingTimeModel.copy(start = "$h:$m"))
                }, getHourFromSet(1).toInt(), getHourFromSet(2).toInt(), true)
            // timePickerDialog.window!!.attributes.windowAnimations =
            //    R.style.dialog_animation_addslovoFU
            timePickerDialog.show()
            timePickerDialog.setOnDismissListener {

                /*  binding.frgmntSetButStart.setText(
                      "$h:$m",null
                  )*/

            }
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
                            if (hourOfDay.toString().length == 1) "0" + (hourOfDay).toString() else (hourOfDay).toString()
                        m =
                            if (minute.toString().length == 1) "0" + minute.toString() else minute.toString()
                        Log.e("endTime", "$h:$m")

                        if (!compare2Times(workingTimeModel.start, "$h:$m"))
                            Toast.makeText(
                                requireContext(),
                                "время должо быть позже времени старта: ${workingTimeModel.start}",
                                Toast.LENGTH_SHORT
                            ).show()
                        else
                            binding.frgmntSetButEnd.setText(
                                "$h:$m", null
                            )
                        // viewModel.setWorkingTime(workingTimeModel.copy(end = "$h:$m"))
                    },
                    getHourFromSet(3).toInt(),
                    getHourFromSet(4).toInt(),
                    true
                )
            // timePickerDialog.window!!.attributes.windowAnimations =
            //   R.style.dialog_animation_addslovoFU
            timePickerDialog.show()
            /* timePickerDialog.setOnDismissListener {

                 binding.frgmntSetButEnd.setText(
                     "$h:$m",null
                 )

             }*/
        }
        setOtherSettings()

    }

    private fun setOtherSettings() {
        binding.frgmntSetAccurEt.setText(workingTimeModel.accuracy.toString())
        (binding.frgmntSetMdEt).setText(workingTimeModel.minDist.toString())
        (binding.frgmntSetTimeAcEt).setText(workingTimeModel.timeOfWaitAccuracy.toString())
        (binding.frgmntSetTimePovtorEt).setText(workingTimeModel.timeOfWorkingWM.toString())
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
        if (listOfDays.size == listOfCheckBox.size) {
            for (i in listOfDays.indices) {
                listOfCheckBox[i].isChecked = listOfDays[i].equals("1")
            }
        }

    }

    private fun saveSettings() {
        var listOfDays1: MutableList<String> = mutableListOf<String>()
        for (cb in listOfCheckBox) {
            listOfDays1.add((cb.isChecked).toIntTxt())
        }
        workingTimeModel = viewModel.getWorkingTime()
        viewModel.setWorkingTime(
            SettingsDomModel(
                listOfDays1,
                binding.frgmntSetButStart.text.toString(),
                binding.frgmntSetButEnd.text.toString(),
                binding.frgmntSetAccurEt.text.toString().toInt(),
                binding.frgmntSetMdEt.text.toString().toInt(),
                binding.frgmntSetTimeAcEt.text.toString().toInt(),
                binding.frgmntSetTimePovtorEt.text.toString().toInt(),
                isEnable = workingTimeModel.isEnable
            )
        )
    }

    private fun Boolean.toIntTxt() = if (this) "1" else "0"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}