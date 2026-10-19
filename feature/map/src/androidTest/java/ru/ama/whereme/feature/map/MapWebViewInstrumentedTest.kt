package ru.ama.whereme.feature.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs in Chromium on Android. The HTML fixture has no network resources. */
@RunWith(AndroidJUnit4::class)
class MapWebViewInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun percentageHeightFillsViewportAndReadyPageReceivesPoints() {
        lateinit var browser: MapWebView
        var page by mutableStateOf(MapPage.LOADING)
        var domHeight by mutableStateOf<Double?>(null)
        var pointCount by mutableStateOf<Int?>(null)
        compose.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapWebView(context, onPage = { page = it }, onExternal = {}, onRoute = {})
                        .also {
                            browser = it
                            it.loadDataWithBaseURL(MAP_URL, HTML, "text/html", "UTF-8", null)
                        }
                },
                onRelease = { it.release() },
            )
        }
        compose.waitUntil(10_000) { page == MapPage.READY }
        compose.runOnIdle {
            browser.evaluateJavascript("document.getElementById('map').clientHeight") {
                domHeight = it.toDouble()
            }
            browser.render("[{\"latitude\":55.0,\"longitude\":37.0}]")
            browser.evaluateJavascript("window.receivedPoints.length") {
                pointCount = it.toInt()
            }
        }
        compose.waitUntil(5_000) { domHeight != null && pointCount != null }
        compose.runOnIdle {
            val height = requireNotNull(domHeight)
            assertTrue("CSS height:100% must not collapse to zero", height > 0)
            assertEquals(
                browser.height / browser.resources.displayMetrics.density.toDouble(),
                height,
                1.0,
            )
            assertEquals(1, pointCount)
        }
    }

    private companion object {
        const val HTML =
            """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body,#map { position:absolute; margin:0; padding:0; width:100%; height:100%; }</style>
            <script>window.receivedPoints=[]; function fromAndroid(points) { window.receivedPoints=points; }</script>
            </head><body><div id="map"></div></body></html>
        """
    }
}
