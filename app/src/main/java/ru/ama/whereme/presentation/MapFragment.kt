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
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentFirstBinding
import ru.ama.whereme.databinding.ItemDateListBinding
import javax.inject.Inject


class MapFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("FragmentFirstBinding == null")
	    private lateinit var viewModel: MapViewModel
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
        setHasOptionsMenu(true)
    }
	
	
	 override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
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
    }

    private fun showPopupText(anchor: View) {
		 val listOfDays: MutableList<String> = mutableListOf<String>()
		 val listOfIds: MutableList<Int> = mutableListOf<Int>()
        val popupWindow = PopupWindow(requireContext())
        ///popupWindow.animationStyle = R.style.dialog_animation
        // val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val adapter = ArrayAdapter(
            requireContext(), R.layout.simple_item_date_list,
            listOfDays
        )

        val binding2 = ItemDateListBinding.inflate(layoutInflater)
		 viewModel.ld_days?.observe(viewLifecycleOwner) {
                    var sdf=""
             listOfDays.clear()
                    for(asd in it)
                    {
                        sdf+="\n"+asd.datestart
                listOfDays.add(asd.datestart)
                listOfIds.add((asd._id).toInt())
                    }

        popupWindow.setBackgroundDrawable(
            ResourcesCompat.getDrawable(
                getResources(),
                R.drawable.nulldr,
                null
            )
        )
                binding2.lvDate.adapter = adapter
				 binding2.lvDate.setOnItemClickListener { parent, view, position, id ->
            viewModel.getDataByDate(listOfDays[position])
                     Toast.makeText(requireContext(),listOfIds[position].toString(),Toast.LENGTH_SHORT).show()
                     viewModel.lldByDay?.observe(viewLifecycleOwner) {

                         val postData= Gson().toJson(it).toString()
                         binding.frgmntLocations.evaluateJavascript("javascript:fromAndroid(${postData})", null)

                         Log.e("getLocationlldByDay",postData)
                     }
                popupWindow.dismiss()
                 }
        popupWindow.isFocusable = true
             popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
             popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.contentView = binding2.root
            /* if (popupWindow.contentView.getParent() != null) {
                 (popupWindow.contentView.getParent() as ViewGroup).removeView(popupWindow.contentView)
             }*/
        popupWindow.showAsDropDown(anchor)
					
                    Toast.makeText(requireContext(),sdf.trim(),Toast.LENGTH_SHORT).show()
                }
		
		
      

    }
	
	
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
/*
        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }*/

        viewModel = ViewModelProvider(this, viewModelFactory)[MapViewModel::class.java]
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
                view?.loadUrl(url!!)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
               


                viewModel.lld2?.observe(viewLifecycleOwner) {
                    //	Toast.makeText(requireContext(),it.toString(),Toast.LENGTH_SHORT).show()
                 ///   Log.e("getLocation22",it.toString())

                  ///  val postData= Gson().toJson(it).toString()
                    /* "[{\"title\": \"место 1\", " +
                             " \"lat\": \"${it?.latitude.toString()}\", " +
                             " \"lon\": \"${it?.longitude.toString()}\","+
                     "\"accuracy\": \"${it?.accuracy.toString()}\"}]";*/
                 ///   binding.frgmntLocations.evaluateJavascript("javascript:fromAndroid(${postData})", null)

                ////    Log.e("getLocation23",postData)
                }
            }
        }
        binding.frgmntLocations.settings.javaScriptEnabled = true

           val url = "https://kol.hhos.ru/map/i.php"
       /* val postData = "{\"tokenJWT\":\"" + URLEncoder.encode(
                settings.getInstance(requireContext()).tokenAutor,
                "UTF-8"
        )+"\",\"mdate\":\"${mdate}\",\"sotrid\":\"${idSotr}\"}"
        wv.postUrl(url, postData.toByteArray())*/
        binding.frgmntLocations.loadUrl(url)
        binding.frgmntLocations.addJavascriptInterface(WebAppInterface(requireContext()), "Android")
		
		//вызов js из android
	//	 binding.wv.loadUrl("javascript:fromAndroid(postData)")
	// использовать это  binding.wv.evaluateJavascript("javascript:fromAndroid(postData)", nil)
	
		/*
		mWebView.evaluateJavascript("javascript:callJS()", new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String value) {
            //Here is the result returned by js
        }
    });
}
		// Android version variable
final int version = Build.VERSION.SDK_INT;
// Because this method can only be used in Android version 4.4, version judgment is needed when using it.
if (version < 18) {
    mWebView.loadUrl("javascript:callJS()");
} else {
    mWebView.evaluateJavascript("javascript:callJS()", new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String value) {
            //Here is the result returned by js
        }
    });
}
		mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setTitle("Alert");
                b.setMessage(message);
                b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                });
                b.setCancelable(false);
                b.create().show();
                return true;
            }

        });
		postData='[{  "title": "место 1",  "lat": "53.642318",  "lon": "55.941752",  "accuracy": "20"},'+ 
							'{  "title": "место 2",  "lat": "53.638187",  "lon": "55.938648",  "accuracy": "202"}, '+
							'{  "title": "место 3",  "lat": "53.630603",  "lon": "55.93168",  "accuracy": "100"}, '+
							'{  "title": "место 4",  "lat": "53.629663",  "lon": "55.9087",  "accuracy": "1500"}, '+
							'{  "title": "место 5",  "lat": "53.622943",  "lon": "55.907565",  "accuracy": "50"}, '+
							'{  "title": "место 6",  "lat": "53.615685",  "lon": "55.907185",  "accuracy": "30"}, '+
							'{  "title": "место 7",  "lat": "53.614053",  "lon": "55.912308",  "accuracy": "800"}, '+
							'{  "title": "место 8",  "lat": "53.623168",  "lon": "55.925588",  "accuracy": "222"}, '+
							'{  "title": "место 9",  "lat": "53.627388",  "lon": "55.939248",  "accuracy": "700"}, '+
							'{ "title": "место 10",  "lat": "53.611296",  "lon": "55.945699",  "accuracy": "333"}]';
		*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
	
	
	 class WebAppInterface(private val mContext: Context) {

        /** Show a toast from the web page  */
        @JavascriptInterface
        fun showToast(toast: String) {
          //  Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()

            val ar=toast.split('#')
            if (ar.size==4)
            {val builder = AlertDialog.Builder(mContext)
            builder.setTitle("маршрут")
            builder.setCancelable(false)
            //builder.setIcon(R.drawable.search);
            builder.setMessage("построить маршрут?\n $toast")
            builder.setNegativeButton("Отмена") { dialog, which ->
                dialog.cancel()
            }
            builder.setPositiveButton("Ок") { dialog, which ->
                val mar="dgis://2gis.ru/routeSearch/rsType/car/from/${ar[1]},${ar[0]}/to/${ar[3]},${ar[2]}"
                    val uri = Uri.parse(mar)//"dgis://")
                    var intent = Intent(Intent.ACTION_VIEW, uri)
// Проверяет, установлено ли хотя бы одно приложение, способное выполнить это действие.
                    val packageManager = (mContext).packageManager!!
                    val activities = packageManager.queryIntentActivities(intent, 0)
                    val isIntentSafe = activities.size > 0
                    if (isIntentSafe) { //Если приложение установлено — запускаем его
                        (mContext).startActivity(intent)
                    } else { // Если не установлено — переходим в Google Play.
                        intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("market://details?id=ru.dublgis.dgismobile")
                        mContext.startActivity(intent)

                    }

            }
            val dialoga = builder.create()
            dialoga.show()}else Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }
    }
	
}