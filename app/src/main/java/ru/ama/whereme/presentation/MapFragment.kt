package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import ru.ama.whereme.R
import ru.ama.whereme.databinding.DatePickerDaysBinding
import ru.ama.whereme.databinding.FragmentMapBinding
import ru.ama.whereme.domain.entity.LocationDbByDays
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
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
            R.id.menu_day_picker -> {
                showPopupDatePicker(requireActivity().findViewById(R.id.menu_day_picker))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPopupDatePicker(anchor: View) {
        val popupWindow = PopupWindow(requireContext())
        popupWindow.setBackgroundDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.nulldr,
                null
            )
        )
        popupWindow.isFocusable = true
        popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        val binding2 = DatePickerDaysBinding.inflate(layoutInflater)
        binding2.frgmntMapDp.setOnDateChangedListener { datePicker, year, monthOfYear, dayOfMonth ->
            val formatter = SimpleDateFormat("dd.MM.yyyy")
            val calendar: Calendar = Calendar.getInstance()
            calendar.set(year, monthOfYear, dayOfMonth)
            val s = formatter.format(calendar.getTime())
            viewModel.getDataByDate(s)
            observeData(s)
            onDataSizeListener = {
                if (it > 0) popupWindow.dismiss()
            }
        }
        popupWindow.contentView = binding2.root
        popupWindow.showAsDropDown(anchor)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setUrl(url: String) {
        if (viewModel.isInternetConnected()) {
            binding.frgmntLocations.loadUrl(url)
            binding.frgmntMapReply.visibility = View.GONE
        } else {
            binding.frgmntMapReply.visibility = View.VISIBLE
            binding.frgmntLocations.loadData(
                getString(R.string.map_no_net),
                "text/html; charset=utf-8",
                "UTF-8"
            );
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = getString(R.string.menu_map)
        viewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
        viewModel.ld_days.observe(viewLifecycleOwner) {
            listDays = it
        }
        if (Build.VERSION.SDK_INT >= 11) {
            val settings: WebSettings = binding.frgmntLocations.settings
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
        }
        binding.frgmntLocations.setBackgroundColor(0)
        binding.frgmntLocations.settings.setGeolocationEnabled(true)
        binding.frgmntLocations.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }
        binding.frgmntLocations.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.context?.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                try {
                    observeData(viewModel.getCurrentDate())
                } catch (e: Exception) {
                }
            }
        }
        binding.frgmntLocations.settings.javaScriptEnabled = true
        val url = getString(R.string.map_url)
        binding.frgmntMapReply.setOnClickListener { setUrl(url) }
        setUrl(url)
        binding.frgmntLocations.addJavascriptInterface(WebAppInterface(requireContext()), getString(
                    R.string.map_android))
    }

    private fun observeData(abSuntitle: String) {
        viewModel.lldByDay?.observe(viewLifecycleOwner) {
            onDataSizeListener?.invoke(it.size)
            if (it.isNotEmpty()) {
                val postData = Gson().toJson(it).toString()
                binding.frgmntLocations.evaluateJavascript(
                    "javascript:fromAndroid(${postData})",
                    null
                )
                (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = abSuntitle
            } else
                Toast.makeText(
                    requireContext(),
                    getString(R.string.map_nodata),
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
                builder.setTitle(mContext.getString(R.string.map_route))
                builder.setCancelable(false)
                //builder.setIcon(R.drawable.search);
                builder.setMessage("построить маршрут?\n $toast")
                builder.setNegativeButton(mContext.getString(R.string.map_cancel)) { dialog, which ->
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