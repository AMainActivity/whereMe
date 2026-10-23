package ru.ama.whereme.ui

import android.app.Application
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.descendants
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.feature.map.MapComponent
import ru.ama.whereme.feature.map.MapScreen
import ru.ama.whereme.ui.theme.WhereMeTheme

/** Exercises the actual WebViewClient and Compose removal/retry, without Chromium or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MapWebViewErrorsTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()
    private lateinit var root: View

    @After
    fun tearDown() {
        compose.runOnUiThread { lifecycle.destroy() }
    }

    @Test
    fun auxiliaryAndTileHttpErrorsKeepTheBrowser() {
        showMap()
        val browser = compose.runOnIdle { browsers().single() }
        for (url in listOf(
            "$ORIGIN/favicon.ico",
            "https://tiles.openfreemap.org/planet/example/0/0/0.pbf",
            "https://example.org/assets/map/map.bundle.js",
        )) {
            compose.runOnIdle { reportHttpError(browser, Request(url)) }
            compose.waitForIdle()
            compose.runOnIdle { assertSame(browser, browsers().single()) }
            compose.onNodeWithText("Повторить").assertDoesNotExist()
        }
    }

    @Test
    fun documentAndMapAssetHttpErrorsShowRetry() {
        showMap()
        for (request in listOf(
            Request("$ORIGIN/assets/map/index.html", mainFrame = true),
            Request("$ORIGIN/assets/map/map.bundle.js"),
            Request("$ORIGIN/assets/map/map.css"),
            Request("$ORIGIN/assets/map/sprites/openfreemap@2x.png"),
        )) {
            compose.runOnIdle { reportHttpError(browsers().single(), request) }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(browsers().isEmpty()) }
            compose.onNodeWithText("Повторить").performClick()
            compose.runOnIdle { assertTrue(browsers().size == 1) }
        }
    }

    private fun showMap() {
        compose.setContent {
            root = LocalView.current
            val component = remember {
                lifecycle.resume()
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = flowOf<Instant?>(null)
                        override fun getLocationById(date: LocalDate) = flowOf(emptyList<LocationDb>())
                    },
                    locateCurrent = { error("Resource failures must not request location") },
                )
            }
            WhereMeTheme(dark = false) { MapScreen(component, darkTheme = false) }
        }
        compose.waitForIdle()
    }

    private fun browsers() = (root as ViewGroup).descendants.filterIsInstance<WebView>().toList()

    private fun reportHttpError(browser: WebView, request: Request) {
        browser.webViewClient.onReceivedHttpError(
            browser, request,
            WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null),
        )
    }

    private class Request(private val address: String, private val mainFrame: Boolean = false) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(address)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
    }
}
