package ru.ama.whereme.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal const val MAP_URL = "https://kol.hhos.ru/map/i.php"

internal enum class MapPage {
    LOADING,
    READY,
    FAILED,
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class MapWebView(
    context: Context,
    private val onPage: (MapPage) -> Unit,
    onExternal: (Uri) -> Unit,
    onRoute: (RouteRequest) -> Unit,
) : WebView(context) {
    private var page = MapPage.LOADING
    private var renderedJson: String? = null

    init {
        // Chromium treats WRAP_CONTENT height as a zero-height HTML viewport. Compose's
        // measured size alone does not override that; the map uses CSS height: 100%.
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(true)
        settings.displayZoomControls = false
        webChromeClient =
            object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback,
                ) {
                    val granted =
                        listOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                            .any {
                                ContextCompat.checkSelfPermission(context, it) ==
                                    PackageManager.PERMISSION_GRANTED
                            }
                    callback.invoke(
                        origin,
                        isAttachedToWindow && isMapOrigin(origin.toUri()) && granted,
                        false,
                    )
                }
            }
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (isMapPage(request.url)) return false
                    if (
                        request.isForMainFrame &&
                            request.hasGesture() &&
                            request.url.scheme in listOf("https", "http")
                    )
                        onExternal(request.url)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    renderedJson = null
                    changePage(MapPage.LOADING)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) changePage(MapPage.FAILED)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    response: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) changePage(MapPage.FAILED)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (page != MapPage.FAILED && url != null && isMapPage(url.toUri()))
                        changePage(MapPage.READY)
                }
            }
        addJavascriptInterface(
            RouteBridge { payload ->
                val request = RouteRequest.parse(payload) ?: return@RouteBridge
                post {
                    if (isAttachedToWindow && isMapPage(url.orEmpty().toUri())) onRoute(request)
                }
            },
            "Android",
        )
    }

    fun render(json: String) {
        if (page == MapPage.READY && renderedJson != json) {
            evaluateJavascript("javascript:fromAndroid($json)", null)
            renderedJson = json
        }
    }

    fun release() {
        removeJavascriptInterface("Android")
        stopLoading()
        webChromeClient = null
        destroy()
    }

    private fun changePage(value: MapPage) {
        page = value
        onPage(value)
    }

    private class RouteBridge(private val onRoute: (String) -> Unit) {
        @JavascriptInterface fun showToast(payload: String) = onRoute(payload)
    }
}

internal fun isMapOrigin(uri: Uri): Boolean =
    uri.scheme == "https" && uri.host == "kol.hhos.ru" && uri.port in listOf(-1, 443)

internal fun isMapPage(uri: Uri): Boolean = isMapOrigin(uri) && uri.path == "/map/i.php"
