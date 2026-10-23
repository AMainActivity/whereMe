package ru.ama.whereme.ui

import java.time.Instant
import java.time.LocalDate
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.descendants
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.feature.map.MapComponent
import ru.ama.whereme.feature.map.MapScreen
import ru.ama.whereme.feature.map.MapIntent
import ru.ama.whereme.ui.theme.WhereMeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MapScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()

    @After
    fun after() {
        compose.runOnUiThread { lifecycle.destroy() }
    }

    @Test
    fun nativeLayerPanelChangesOnlyRequestedLayer() {
        lateinit var component: MapComponent
        compose.setContent {
            component = remember {
                lifecycle.resume()
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = flowOf<Instant?>(null)
                        override fun getLocationById(date: LocalDate) = flowOf(emptyList<LocationDb>())
                    },
                    locateCurrent = { error("Opening layers must not request location") },
                )
            }
            WhereMeTheme(dark = true) {
                Surface(Modifier.fillMaxSize()) { MapScreen(component, darkTheme = true) }
            }
        }
        compose.onNodeWithContentDescription("Слои карты").performClick()
        compose.onNodeWithText("Предыдущие точки").performClick()
        compose.runOnIdle {
            assertFalse(component.state.value.layers.previousPointsVisible)
            assertTrue(component.state.value.layers.trackVisible)
        }
        compose.onNodeWithText("Линия трека").performClick()
        compose.runOnIdle {
            assertFalse(component.state.value.layers.previousPointsVisible)
            assertFalse(component.state.value.layers.trackVisible)
        }
    }

    @Test
    fun embeddedBrowserUsesContainerHeightInsteadOfHtmlContentHeight() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            val component = remember {
                lifecycle.resume()
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = kotlinx.coroutines.flow.flowOf<Instant?>(null)
                        override fun getLocationById(date: LocalDate) = flowOf(emptyList<LocationDb>())
                    },
                    locateCurrent = { error("Location not requested in this test") },
                )
            }
            WhereMeTheme(dark = true) {
                Surface(Modifier.fillMaxSize()) { MapScreen(component, darkTheme = true) }
            }
        }
        compose.runOnIdle {
            val browser = (root as ViewGroup).descendants.filterIsInstance<WebView>().single()
            // Chromium forces the HTML layout height to zero for WRAP_CONTENT, even when
            // Compose gives the native View a nonzero measured height. Robolectric cannot
            // execute Chromium; check its layout contract on the real composed screen.
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, browser.layoutParams.height)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, browser.layoutParams.width)
        }
    }

    @Test
    fun changingDayClearsTheOldTrackWhileTheNewHistoryIsStillLoading() {
        lateinit var component: MapComponent
        lateinit var root: View
        val initialDay = LocalDate.now()
        val nextDay = initialDay.plusDays(1)
        compose.setContent {
            root = LocalView.current
            component = remember {
                lifecycle.resume()
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = flowOf<Instant?>(null)
                        override fun getLocationById(date: LocalDate) = flow {
                            if (date == initialDay) emit(listOf(
                                LocationDb(1, "1000", Instant.ofEpochMilli(1000), null, null, 55.0, 37.0, 1, 5f, 0f, 0),
                            ))
                            awaitCancellation()
                        }
                    },
                    locateCurrent = { error("Location not requested in this test") },
                )
            }
            WhereMeTheme(dark = false) { MapScreen(component, darkTheme = false) }
        }
        lateinit var browser: WebView
        compose.waitUntil { component.state.value.points.isNotEmpty() }
        compose.runOnIdle {
            browser = (root as ViewGroup).descendants.filterIsInstance<WebView>().single()
            val bridge = shadowOf(browser).getJavascriptInterface("Android")
            bridge.javaClass.getDeclaredMethod("postMessage", String::class.java).apply { isAccessible = true }
                .invoke(bridge, "{\"type\":\"ready\"}")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(shadowOf(browser).lastEvaluatedJavascript.contains("\"_id\":1"))
            component.accept(MapIntent.SelectDate(nextDay))
        }
        compose.waitUntil { component.state.value.date == nextDay }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(
                "window.whereMeMap.setData({\"day\":\"$nextDay\",\"points\":[]})",
                shadowOf(browser).lastEvaluatedJavascript,
            )
        }
    }
    @Test
    fun explicitThemeChangesReachTheExistingBrowserWithoutReloadingHistory() {
        val dark = mutableStateOf(false)
        lateinit var root: View
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
                    locateCurrent = { error("Theme switch must not request location") },
                )
            }
            WhereMeTheme(dark = dark.value) { MapScreen(component, darkTheme = dark.value) }
        }
        lateinit var browser: WebView
        compose.runOnIdle {
            browser = (root as ViewGroup).descendants.filterIsInstance<WebView>().single()
            val bridge = shadowOf(browser).getJavascriptInterface("Android")
            bridge.javaClass.getDeclaredMethod("postMessage", String::class.java).apply { isAccessible = true }
                .invoke(bridge, "{\"type\":\"ready\"}")
        }
        compose.waitForIdle()
        for (value in listOf(true, false)) {
            compose.runOnIdle { dark.value = value }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(browser, (root as ViewGroup).descendants.filterIsInstance<WebView>().single())
                val script = shadowOf(browser).lastEvaluatedJavascript
                assertTrue(script.startsWith("window.whereMeMap.setTheme("))
                assertTrue(script.contains("\"dark\":$value"))
            }
        }
    }

}
