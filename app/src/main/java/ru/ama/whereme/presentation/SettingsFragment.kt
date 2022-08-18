package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.data.database.SettingsDomainModel
import ru.ama.whereme.databinding.DialWorktimeBinding
import ru.ama.whereme.databinding.FragmentSettingsBinding
import ru.ama.whereme.databinding.ItemDateListBinding
import javax.inject.Inject


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentSettingsBinding == null")
    private lateinit var viewModel: SettingsViewModel
    private val component by lazy {
        (requireActivity().application as MyApp).component
    }
	 private lateinit var workingTimeModel:SettingsDomainModel		
	

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    override fun onAttach(context: Context) {
        component.inject(this)

        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setHasOptionsMenu(true)
    }


    /* override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
         menuInflater.inflate(R.menu.menu_map_fragment, menu)
     }

     override fun onOptionsItemSelected(item: MenuItem): Boolean {

         return when (item.itemId) {
             R.id.menu_day_list -> {
                 showPopupText(requireActivity().findViewById(R.id.menu_day_list))
                 true
             }
             else -> super.onOptionsItemSelected(item)
         }
     }*/

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

    fun getHourFromSet(id:Int):String{
        val start= workingTimeModel.start.split(":")
        val end= workingTimeModel.end.split(":")
        var res=""
        when(id)
        {
            1->res=start[0]
            2->res=start[1]
            3->res=end[0]
            4->res=end[1]

        }
        return res
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        viewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]
        workingTimeModel=viewModel.getWorkingTime()
        binding.frgmntSetButWdays.setOnClickListener {
            showPopupDays(binding.frgmntSetButWdays)
        }
        binding.frgmntSetButStart.setOnClickListener {
            val timePickerDialog =
                TimePickerDialog(requireContext(), { view, hourOfDay, minute ->

                    val h =
                        if (hourOfDay.toString().length == 1) "0" + (hourOfDay).toString() else (hourOfDay).toString()
                    val m =
                        if (minute.toString().length == 1) "0" + minute.toString() else minute.toString()
                    Log.e("Time", "$h:$m")
                    viewModel.setWorkingTime(workingTimeModel.copy(start="$h:$m"))
                }, getHourFromSet(1).toInt(),getHourFromSet(2).toInt(), true)
           // timePickerDialog.window!!.attributes.windowAnimations =
            //    R.style.dialog_animation_addslovoFU
            timePickerDialog.show()
            timePickerDialog.setOnDismissListener {

               // root.frgmnt_set_but_start.setText(Html.fromHtml(if (res.length>2) "Время старта: <span style='color:red;'> ${getHourFromSet(1)}:${getHourFromSet(2)} </span>" else "Рабочие дни" )
                //)

            }
        }
        binding.frgmntSetButEnd.setOnClickListener {
                val timePickerDialog =
                    TimePickerDialog(requireContext(), TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
                        val h=if (hourOfDay.toString().length==1) "0"+(hourOfDay).toString() else (hourOfDay).toString()
                        val m=if (minute.toString().length==1) "0"+minute.toString() else minute.toString()
                        Log.e("endTime","$h:$m")

                      //  if (!utils.getInstance(requireContext()).compare2Times(jWorkTime.start,"$h:$m"))
                     //       utils.getInstance(requireContext()).custToast("время окончания должно быть больше времени старта",true)
                      //  else
                       //     settings.getInstance(requireContext()).worktime= Gson().toJson(adrrResponse.workTimeSetting(jWorkTime.days,  jWorkTime.start,"$h:$m")).toString()

                        viewModel.setWorkingTime(workingTimeModel.copy(end="$h:$m"))
                                                                                          }, getHourFromSet(3).toInt(),getHourFromSet(4).toInt(), true)
               // timePickerDialog.window!!.attributes.windowAnimations =
                 //   R.style.dialog_animation_addslovoFU
                timePickerDialog.show()
                timePickerDialog.setOnDismissListener {

                    //root.frgmnt_set_but_end.setText(Html.fromHtml(if (res.length>2) "Время окончания: <span style='color:red;'> ${getHourFromSet(3)}:${getHourFromSet(4)} </span>" else "Рабочие дни" )
                   // )

                }
            }
    }


    private fun showPopupDays(anchor: View) {
        val popupWindow = PopupWindow(requireContext())
        ///popupWindow.animationStyle = R.style.dialog_animation
        // val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        popupWindow.setBackgroundDrawable(
            ResourcesCompat.getDrawable(
                getResources(),
                R.drawable.nulldr,
                null
            )
        )
        popupWindow.isFocusable = true
        popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        val binding2 = DialWorktimeBinding.inflate(layoutInflater)
		val listOfDays=workingTimeModel.days
		val listOfCheckBox= listOf(binding2.frgmntSetCbD1,
		binding2.frgmntSetCbD2,
		binding2.frgmntSetCbD3,
		binding2.frgmntSetCbD4,
		binding2.frgmntSetCbD5,
		binding2.frgmntSetCbD6,
		binding2.frgmntSetCbD7
		)
		if (listOfDays.size==listOfCheckBox.size)
		{
			for (i in listOfDays.indices)
			{
				listOfCheckBox[i].isChecked= listOfDays[i].equals("1")
			}
		}
		popupWindow.setOnDismissListener{
        var listOfDays1: MutableList<String> = mutableListOf<String>()
			for (cb in listOfCheckBox)
			{
				listOfDays1.add((cb.isChecked).toIntTxt())
			}
			viewModel.setWorkingTime(SettingsDomainModel(listOfDays1,workingTimeModel.start,workingTimeModel.end))
		}
        popupWindow.contentView = binding2.root
        //popupWindow.dismiss()
        popupWindow.showAsDropDown(anchor)

    }

private fun Boolean.toIntTxt()= if (this) "1" else "0"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}