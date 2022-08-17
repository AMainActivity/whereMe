package ru.ama.whereme.presentation

import android.annotation.SuppressLint
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


    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        viewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]
        binding.frgmntSetButWdays.setOnClickListener {
            showPopupDays(binding.frgmntSetButWdays)
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

        popupWindow.contentView = binding2.root
        //popupWindow.dismiss()
        popupWindow.showAsDropDown(anchor)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}