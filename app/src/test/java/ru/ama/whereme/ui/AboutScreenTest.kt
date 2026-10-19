package ru.ama.whereme.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ama.whereme.R
import ru.ama.whereme.feature.about.AboutComponent
import ru.ama.whereme.feature.about.AboutScreen
import ru.ama.whereme.ui.theme.WhereMeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()

    @After
    fun after() {
        compose.runOnUiThread { lifecycle.destroy() }
    }

    @Test
    fun fullLicenseCanBeOpenedAndClosedWithoutNetwork() {
        compose.setContent {
            val component =
                androidx.compose.runtime.remember {
                    lifecycle.resume()
                    AboutComponent(DefaultComponentContext(lifecycle))
                }
            WhereMeTheme(dark = true) {
                Surface { AboutScreen(component, "whereMe", "test", R.raw.aboutlibraries) }
            }
        }
        compose.onNodeWithText("Библиотеки и лицензии").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Activity", substring = false))
        compose.onNodeWithText("Activity", substring = false).performClick()
        compose.onNodeWithText("TERMS AND CONDITIONS", substring = true).assertExists()
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithText("Версия test").assertExists()
    }
}
