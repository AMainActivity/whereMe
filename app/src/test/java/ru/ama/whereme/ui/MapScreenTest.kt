package ru.ama.whereme.ui

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.descendants
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
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
    fun embeddedBrowserUsesContainerHeightInsteadOfHtmlContentHeight() {
        lateinit var root: View
        compose.setContent {
            root = LocalView.current
            val component = remember {
                lifecycle.resume()
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = kotlinx.coroutines.flow.flowOf<Long?>(null)
                        override fun getLocationById(date: String) = flowOf(emptyList<LocationDb>())
                    },
                )
            }
            WhereMeTheme(dark = true) {
                Surface(Modifier.fillMaxSize()) { MapScreen(component) }
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
}
