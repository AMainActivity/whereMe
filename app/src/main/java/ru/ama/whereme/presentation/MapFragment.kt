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
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.databinding.DatePickerDaysBinding
import ru.ama.whereme.databinding.FragmentFirstBinding
import ru.ama.whereme.databinding.ItemDateListBinding
import ru.ama.whereme.domain.entity.LocationDbByDays
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class MapFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("FragmentFirstBinding == null")
    private lateinit var viewModel: MapViewModel
    lateinit var listDays: List<LocationDbByDays>
    private val component by lazy {
        (requireActivity().application as MyApp).component
    }
    var onDataSizeListener: ((Int) -> Unit)? = null

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
        menuInflater.inflate(R.menu.menu_map_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
           /* R.id.menu_day_list -> {
                showPopupText(requireActivity().findViewById(R.id.menu_day_list))
                true
            }*/
           /* R.id.menu_set_frgmnt -> {

                findNavController().navigate(R.id.action_FirstFragment_to_SettingsFragment)
                true
            }*/
            R.id.menu_day_picker -> {

                showPopupDatePicker(requireActivity().findViewById(R.id.menu_day_picker))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun showPopupDatePicker(anchor: View) {
        val popupWindow = PopupWindow(requireContext())
        ///popupWindow.animationStyle = R.style.dialog_animation
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
        val binding2 = DatePickerDaysBinding.inflate(layoutInflater)
        binding2.frgmntMapDp.setOnDateChangedListener { datePicker,  year,  monthOfYear,  dayOfMonth ->
            val formatter = SimpleDateFormat("dd.MM.yyyy")
            val calendar: Calendar = Calendar.getInstance()
            calendar.set(year,monthOfYear,dayOfMonth)
            val s= formatter.format(calendar.getTime())
            viewModel.getDataByDate(s)
            observeData(s)
            onDataSizeListener={
				if (it>0) popupWindow.dismiss()
			}
        }
        popupWindow.contentView = binding2.root
        // popupWindow.dismiss()
        popupWindow.showAsDropDown(anchor)

    }


    /*private fun showPopupText(anchor: View) {
        // val listDays = viewModel.getListOfDays()
        if (listDays != null) {
            val listOfDays: MutableList<String> = mutableListOf<String>()
            val listOfIds: MutableList<Int> = mutableListOf<Int>()
            val popupWindow = PopupWindow(requireContext())
            ///popupWindow.animationStyle = R.style.dialog_animation
            // val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_list_item_1,
                listOfDays
            )
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
            val binding2 = ItemDateListBinding.inflate(layoutInflater)
            //  viewModel.ld_days?.observe(viewLifecycleOwner) {
            var sdf = ""
            listOfDays.clear()
            for (asd in listDays) {
                sdf += "\n" + asd.datestart
                listOfDays.add(asd.datestart)
                listOfIds.add((asd._id).toInt())
            }


            binding2.lvDate.adapter = adapter
            binding2.lvDate.setOnItemClickListener { parent, view, position, id ->
                viewModel.getDataByDate(listOfDays[position])
                // Toast.makeText(requireContext(),listOfIds[position].toString(),Toast.LENGTH_SHORT).show()
                viewModel.lldByDay?.observe(viewLifecycleOwner) {

                    val postData = Gson().toJson(it).toString()
                    binding.frgmntLocations.evaluateJavascript(
                        "javascript:fromAndroid(${postData})",
                        null
                    )

                    Log.e("getLocationlldByDay", postData)
                }
                popupWindow.dismiss()
                setActionBarSubTitle(listOfDays[position])
            }


            popupWindow.contentView = binding2.root
            popupWindow.dismiss()
            popupWindow.showAsDropDown(anchor)

            if (binding2.lvDate.adapter.count == 0) Toast.makeText(
                requireContext(),
                "пока нет данных,\nПопробуйте позднее...",
                Toast.LENGTH_SHORT
            ).show()
            //   }
        } else
            Toast.makeText(
                requireContext(),
                "пока нет данных,\nПопробуйте позднее...",
                Toast.LENGTH_SHORT
            ).show()

    }
*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    private fun setUrl(url: String) {
        if (viewModel.isInternetConnected())
        { binding.frgmntLocations.loadUrl(url)
        binding.frgmntMapReply.visibility=View.GONE
        }
        else {
            binding.frgmntMapReply.visibility=View.VISIBLE
            binding.frgmntLocations.loadData("Нет подключения, нет возможноти показать карту. Функционал получения местоположений доступен без наличия интернета", "text/html; charset=utf-8", "UTF-8");
           /* val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Нет подключения к сети...")
                .setCancelable(false)
                .setPositiveButton("повторить") { dialog, id ->
                    setUrl(url)
                }
                .setNegativeButton("выйти") { dialog, id ->
                    dialog.cancel()
                    requireActivity().finish()
                }
            val alert: AlertDialog = builder.create()
            alert.show()*/
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle ="Карта"
        viewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
        viewModel.ld_days.observe(viewLifecycleOwner) {
            listDays = it
        }

        if (Build.VERSION.SDK_INT >= 11) {
            val settings: WebSettings = binding.frgmntLocations.settings
            settings.setBuiltInZoomControls(false)
            settings.setDisplayZoomControls(false)
            //settings.setTextZoom(80)
        }
        binding.frgmntLocations.setBackgroundColor(0)
        binding.frgmntLocations.getSettings().setGeolocationEnabled(true)
        binding.frgmntLocations.setWebChromeClient(object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        })


        //wv.loadDataWithBaseURL(null,getString(R.string.frgmnt_instructions),"text/html","UTF-8","")
        binding.frgmntLocations.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.getContext()?.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
                //view?.loadUrl(url!!)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                try {
                    observeData(viewModel.getCurrentDate())
                }
                catch (e:Exception){}
            }
        }
        binding.frgmntLocations.settings.javaScriptEnabled = true

        val url = "https://kol.hhos.ru/map/i.php"
        binding.frgmntMapReply.setOnClickListener { setUrl(url) }
        setUrl(url)
        binding.frgmntLocations.addJavascriptInterface(WebAppInterface(requireContext()), "Android")

    }


    private fun observeData(abSuntitle:String)
    {
        viewModel.lldByDay?.observe(viewLifecycleOwner) {
            onDataSizeListener?.invoke(it.size)
            if (it.size>=1) {
                val postData = Gson().toJson(it).toString()
                binding.frgmntLocations.evaluateJavascript(
                    "javascript:fromAndroid(${postData})",
                    null
                )
                // popupWindow.dismiss()
                (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =abSuntitle
            }
            else

                Toast.makeText(
                    requireContext(),
                    "нет данных",
                    Toast.LENGTH_SHORT
                ).show()
            Log.e("getLocationlldByDay", it.toString())
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.lldByDay?.removeObservers(viewLifecycleOwner)
        _binding = null
    }


    class WebAppInterface(private val mContext: Context) {

        @JavascriptInterface
        fun showToast(toast: String) {

            val ar = toast.split('#')
            if (ar.size == 4) {
                val builder = AlertDialog.Builder(mContext)
                builder.setTitle("маршрут")
                builder.setCancelable(false)
                //builder.setIcon(R.drawable.search);
                builder.setMessage("построить маршрут?\n $toast")
                builder.setNegativeButton("Отмена") { dialog, which ->
                    dialog.cancel()
                }
                builder.setPositiveButton("Ок") { dialog, which ->
                    val mar =
                        "dgis://2gis.ru/routeSearch/rsType/car/from/${ar[1]},${ar[0]}/to/${ar[3]},${ar[2]}"
                    val uri = Uri.parse(mar)//"dgis://")
                    var intent = Intent(Intent.ACTION_VIEW, uri)
                    val packageManager = (mContext).packageManager!!
                    val activities = packageManager.queryIntentActivities(intent, 0)
                    val isIntentSafe = activities.size > 0
                    if (isIntentSafe) {
                        (mContext).startActivity(intent)
                    } else {
                        intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("market://details?id=ru.dublgis.dgismobile")
                        mContext.startActivity(intent)

                    }

                }
                val dialoga = builder.create()
                dialoga.show()
            } else Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }
    }

}