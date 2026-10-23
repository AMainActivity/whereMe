package ru.ama.whereme.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAP_SCHEME = "https"
private const val MAP_HOST = "appassets.androidplatform.net"
private const val MAP_ASSETS_PATH = "/assets/map/"
private const val MAP_PATH = "${MAP_ASSETS_PATH}index.html"
private const val HTTPS_PORT = 443
private const val IMPLICIT_PORT = -1
private const val JS_BRIDGE_NAME = "Android"
private const val TILE_HOST = "tiles.openfreemap.org"
internal const val MAP_URL = "$MAP_SCHEME://$MAP_HOST$MAP_PATH"

internal enum class MapPage { LOADING, READY, FAILED }

@Serializable
internal data class MapTheme(val background: String, val surface: String, val text: String, val primary: String, val dark: Boolean)

@Serializable
private data class MapEvent(val type: String, val ids: List<String> = emptyList(), val failed: Boolean = false)

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class MapWebView(
    context: Context,
    private val onPage: (MapPage) -> Unit,
    onExternal: (Uri) -> Unit,
    onSelection: (List<String>) -> Unit,
    onTilesFailed: (Boolean) -> Unit,
) : WebView(context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var page = MapPage.LOADING
    private var released = false
    private var renderedData: Pair<String, String>? = null
    private var renderedLayers: MapLayers? = null
    private var renderedTheme: MapTheme? = null
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        // Chromium needs a non-WRAP_CONTENT viewport for HTML height:100%.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = "${settings.userAgentString} whereMe/${context.packageName}"
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                if (request.url.scheme == MAP_SCHEME && request.url.host == TILE_HOST &&
                    request.url.port in listOf(IMPLICIT_PORT, HTTPS_PORT) && request.method == "GET"
                ) return null
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (isMapPage(request.url)) return false
                if (request.isForMainFrame && request.hasGesture() && request.url.scheme in listOf("https", "http")) {
                    onExternal(request.url)
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                renderedData = null
                renderedLayers = null
                renderedTheme = null
                changePage(MapPage.LOADING)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame || isMapAsset(request.url)) changePage(MapPage.FAILED)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame || isMapAsset(request.url)) changePage(MapPage.FAILED)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // FAILED removes AndroidView from composition; onRelease destroys this WebView.
                changePage(MapPage.FAILED)
                return true
            }
        }
        addJavascriptInterface(EventBridge { payload ->
            val event = try { json.decodeFromString<MapEvent>(payload) }
            catch (_: SerializationException) { return@EventBridge }
            post {
                if (!released && isMapPage(url.orEmpty().toUri())) {
                    when (event.type) {
                        "ready" -> if (page == MapPage.LOADING) changePage(MapPage.READY)
                        "failed" -> changePage(MapPage.FAILED)
                        "selection" -> if (page == MapPage.READY) onSelection(event.ids)
                        "tiles" -> if (page == MapPage.READY) onTilesFailed(event.failed)
                    }
                }
            }
        }, JS_BRIDGE_NAME)
    }

    fun configure(layers: MapLayers, theme: MapTheme) {
        if (page != MapPage.READY || released) return
        if (renderedTheme != theme) {
            call("setTheme", json.encodeToString(theme))
            renderedTheme = theme
        }
        // Apply visibility before initial fit, including after WebView recreation.
        if (renderedLayers != layers) {
            call("setOptions", json.encodeToString(layers))
            renderedLayers = layers
        }
    }

    fun render(day: String, points: String?) {
        if (page != MapPage.READY || released) return
        // A loading/failed new day must not display the previous day's track.
        // Refreshing the same day keeps its last data and the user's camera position.
        val visiblePoints = points ?: if (renderedData?.first == day) return else EMPTY_POINTS_JSON
        val data = day to visiblePoints
        if (renderedData != data) {
            call("setData", "{\"day\":${json.encodeToString(day)},\"points\":$visiblePoints}")
            renderedData = data
        }
    }

    fun fitAll() = call("fitAll")
    fun showLast() = call("showLast")
    fun retryTiles() = call("retryTiles")
    fun select(id: String?) = call("select", json.encodeToString(id))
    fun showLocation(latitude: Double, longitude: Double, accuracy: Float) =
        call("showLocation", "{\"latitude\":$latitude,\"longitude\":$longitude,\"accuracy\":$accuracy}")

    private fun call(function: String, argument: String = "") {
        if (!released && page == MapPage.READY) evaluateJavascript("window.whereMeMap.$function($argument)", null)
    }

    fun release() {
        released = true
        removeJavascriptInterface(JS_BRIDGE_NAME)
        stopLoading()
        destroy()
    }

    private fun changePage(value: MapPage) {
        if (!released) { page = value; onPage(value) }
    }

    private class EventBridge(private val receive: (String) -> Unit) {
        @JavascriptInterface fun postMessage(payload: String) = receive(payload)
    }
}

private const val EMPTY_POINTS_JSON = "[]"

internal fun isMapOrigin(uri: Uri): Boolean =
    uri.scheme == MAP_SCHEME && uri.host == MAP_HOST && uri.port in listOf(IMPLICIT_PORT, HTTPS_PORT)

internal fun isMapPage(uri: Uri): Boolean = isMapOrigin(uri) && uri.path == MAP_PATH

// Auxiliary same-origin requests (for example /favicon.ico) are not map dependencies.
private fun isMapAsset(uri: Uri): Boolean = isMapOrigin(uri) && uri.path?.startsWith(MAP_ASSETS_PATH) == true
