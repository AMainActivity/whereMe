package ru.ama.whereme.presentation

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import com.google.gson.Gson
import ru.ama.whereme.R


class dialogWorkTime(internal var context: Context) : Dialog(context) {

    lateinit var cb1: AppCompatCheckBox
    lateinit var cb2: AppCompatCheckBox
    lateinit var cb3: AppCompatCheckBox
    lateinit var cb4: AppCompatCheckBox
    lateinit var cb5: AppCompatCheckBox
    lateinit var cb6: AppCompatCheckBox
    lateinit var cb7: AppCompatCheckBox
    /*init {
        window!!.requestFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dial_worktime)
        window!!.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        window!!.setGravity(Gravity.CENTER)
        window!!.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val jWorkTime= Gson().fromJson(
            settings.getInstance(context).worktime,
            adrrResponse.WorkTime::class.java
        )
        val mdays= jWorkTime.days.split(";")
        val start= jWorkTime.start.split(":")
        val end= jWorkTime.end.split(":")
        cb1= findViewById(R.id.frgmnt_set_cb_d1)
        cb2= findViewById(R.id.frgmnt_set_cb_d2)
        cb3= findViewById(R.id.frgmnt_set_cb_d3)
        cb4= findViewById(R.id.frgmnt_set_cb_d4)
        cb5= findViewById(R.id.frgmnt_set_cb_d5)
        cb6= findViewById(R.id.frgmnt_set_cb_d6)
        cb7= findViewById(R.id.frgmnt_set_cb_d7)

        cb1.isChecked=if (mdays[0].equals("1")) true else false
        cb2.isChecked=if (mdays[1].equals("1")) true else false
        cb3.isChecked=if (mdays[2].equals("1")) true else false
        cb4.isChecked=if (mdays[3].equals("1")) true else false
        cb5.isChecked=if (mdays[4].equals("1")) true else false
        cb6.isChecked=if (mdays[5].equals("1")) true else false
        cb7.isChecked=if (mdays[6].equals("1")) true else false
        var myButtonClickListener: CompoundButton.OnCheckedChangeListener = object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                when (p0?.id) {
                    R.id.frgmnt_set_cb_d1 -> {

                    }
                }
                val res="${if (cb1.isChecked) 1 else 0};" +
                        "${if (cb2.isChecked) 1 else 0};" +
                        "${if (cb3.isChecked) 1 else 0};" +
                        "${if (cb4.isChecked) 1 else 0};" +
                        "${if (cb5.isChecked) 1 else 0};" +
                        "${if (cb6.isChecked) 1 else 0};" +
                        "${if (cb7.isChecked) 1 else 0}"

                Log.e("myButtonClickListener", res)
            }
        }
        cb1.setOnCheckedChangeListener(myButtonClickListener)
        cb2.setOnCheckedChangeListener(myButtonClickListener)
        cb3.setOnCheckedChangeListener(myButtonClickListener)
        cb4.setOnCheckedChangeListener(myButtonClickListener)
        cb5.setOnCheckedChangeListener(myButtonClickListener)
        cb6.setOnCheckedChangeListener(myButtonClickListener)
        cb7.setOnCheckedChangeListener(myButtonClickListener)
        frgmnt_dwt_but_ok.setOnClickListener {
            val res="${if (cb1.isChecked) 1 else 0};" +
                "${if (cb2.isChecked) 1 else 0};" +
                "${if (cb3.isChecked) 1 else 0};" +
                "${if (cb4.isChecked) 1 else 0};" +
                "${if (cb5.isChecked) 1 else 0};" +
                "${if (cb6.isChecked) 1 else 0};" +
                "${if (cb7.isChecked) 1 else 0}"
            val k2= Gson().toJson(adrrResponse.workTimeSetting(res, jWorkTime.start, jWorkTime.end)).toString()
            settings.getInstance(context).worktime=k2
            dismiss()
        }
show()

    }*/


    fun setMessage(ti: String) {
    }


}