package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.annotation.SuppressLint
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.core.view.isVisible
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.R
import ru.ama.whereme.databinding.DatePickerDaysBinding
import ru.ama.whereme.databinding.FragmentMapBinding
import ru.ama.whereme.presentation.map.RouteRequest
import ru.ama.whereme.presentation.map.DayLocations
import java.text.SimpleDateFormat
import java.util.*


@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("FragmentFirstBinding == null")
    private lateinit var viewModel: MapViewModel
    private enum class PageState { LOADING, READY, FAILED }
    private var pageState = PageState.LOADING
    private var datePopup: PopupWindow? = null
    private var errorSnackbar: com.google.android.material.snackbar.Snackbar? = null
    private var routeDialog: AlertDialog? = null



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
        datePopup?.dismiss()
        val popupWindow = PopupWindow(requireContext()).also { datePopup = it }
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
        val datePicker = binding2.frgmntMapDp
        datePicker.init(datePicker.year, datePicker.month, datePicker.dayOfMonth) { _, year, monthOfYear, dayOfMonth ->
            val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
            val calendar: Calendar = Calendar.getInstance()
            calendar.set(year, monthOfYear, dayOfMonth)
            val s = formatter.format(calendar.getTime())
            viewModel.getDataByDate(s)
            popupWindow.dismiss()
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
        pageState = PageState.LOADING
        if (viewModel.isInternetConnected()) {
            binding.frgmntLocations.loadUrl(url)
            binding.frgmntMapReply.visibility = View.GONE
        } else {
            binding.frgmntMapReply.visibility = View.VISIBLE
            binding.frgmntLocations.loadData(
                getString(R.string.map_no_net),
                MIME_TYPE,
                ENCODING
            );
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =
            getString(R.string.menu_map)
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locations.collect(::renderLocations)
            }
        }
        binding.frgmntLocations.settings.builtInZoomControls = false
        binding.frgmntLocations.settings.displayZoomControls = false
        binding.frgmntLocations.setBackgroundColor(0)
        binding.frgmntLocations.settings.setGeolocationEnabled(true)
        binding.frgmntLocations.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (_binding == null || !isAdded) {
                    callback.invoke(origin, false, false)
                    return
                }
                val locationGranted = listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
                ).any { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
                callback.invoke(origin, isMapOrigin(Uri.parse(origin)) && locationGranted, false)
            }
        }
        binding.frgmntLocations.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (_binding == null || !isAdded) return true
                if (isMapPage(request.url)) return false
                if (request.isForMainFrame && request.hasGesture() &&
                    request.url.scheme in listOf("https", "http")
                ) openExternal(request.url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageState = PageState.LOADING
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showMapLoadFailure()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showMapLoadFailure()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (_binding != null && pageState != PageState.FAILED && url != null && isMapPage(Uri.parse(url))) {
                    pageState = PageState.READY
                    if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        renderLocations(viewModel.locations.value)
                    }
                }
            }
        }
        binding.frgmntLocations.settings.javaScriptEnabled = true
        val url = getString(R.string.map_url)
        binding.frgmntMapReply.setOnClickListener { setUrl(url) }
        val webView = binding.frgmntLocations
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(WebAppInterface { payload ->
            val route = RouteRequest.parse(payload) ?: return@WebAppInterface
            webView.post {
                val current = _binding ?: return@post
                if (current.frgmntLocations === webView && isResumed &&
                    isMapPage(Uri.parse(webView.url ?: ""))
                ) showRouteDialog(route)
            }
        }, getString(R.string.map_android))
        setUrl(url)
    }

    private fun showMapLoadFailure() {
        pageState = PageState.FAILED
        _binding?.frgmntMapReply?.isVisible = true
    }

    private fun renderLocations(state: LoadState<DayLocations>) {
        val day = (state as? LoadState.Ready)?.value
        if (day != null && day.date != viewModel.selectedDate) return
        errorSnackbar?.dismiss()
        errorSnackbar = null
        if (state == LoadState.Failed) {
            errorSnackbar = com.google.android.material.snackbar.Snackbar.make(binding.root, R.string.storage_read_failed,
                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.storage_retry) { viewModel.retryLocations() }.also { it.show() }
        }
        if (pageState != PageState.READY) return
        val postData = day?.json ?: "[]"
        binding.frgmntLocations.evaluateJavascript(String.format(getString(R.string.map_js), postData), null)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = viewModel.selectedDate
    }

    private fun isMapOrigin(uri: Uri): Boolean {
        val expected = Uri.parse(getString(R.string.map_url))
        return uri.scheme == "https" && uri.host == expected.host && uri.port in listOf(-1, 443)
    }

    private fun isMapPage(uri: Uri): Boolean =
        isMapOrigin(uri) && uri.path == Uri.parse(getString(R.string.map_url)).path

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.map_external_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRouteDialog(route: RouteRequest) {
        if (routeDialog?.isShowing == true) return
        val description = "${route.fromLatitude}, ${route.fromLongitude} → ${route.toLatitude}, ${route.toLongitude}"
        routeDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.map_route)
            .setMessage(getString(R.string.map_get_route, description))
            .setNegativeButton(R.string.map_cancel, null)
            .setPositiveButton(R.string.string_ok) { _, _ ->
                val uri = Uri.parse(getString(
                    R.string.map_route_url, route.fromLongitude.toString(), route.fromLatitude.toString(),
                    route.toLongitude.toString(), route.toLatitude.toString()
                ))
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    openExternal(Uri.parse(getString(R.string.map_2gisapp)))
                }
            }
            .show()
    }

    override fun onDestroyView() {
        pageState = PageState.LOADING
        datePopup?.dismiss()
        datePopup = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        routeDialog?.dismiss()
        routeDialog = null
        val webView = binding.frgmntLocations
        webView.removeJavascriptInterface(getString(R.string.map_android))
        webView.stopLoading()
        webView.webChromeClient = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        _binding = null
        super.onDestroyView()
    }

    class WebAppInterface(private val onRoute: (String) -> Unit) {
        @JavascriptInterface
        fun showToast(payload: String) = onRoute(payload)
    }

    private companion object {
        const val ENCODING = "UTF-8"
        const val MIME_TYPE = "text/html; charset=utf-8"
    }
}