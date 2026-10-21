package ru.ama.whereme.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.ama.whereme.R
import ru.ama.whereme.feature.about.AboutComponent
import ru.ama.whereme.feature.about.AboutScreen
import ru.ama.whereme.navigation.Screen
import ru.ama.whereme.ui.theme.WhereMeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h873dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenTest {
    private lateinit var contentView: View
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()

    @After
    fun after() {
        compose.runOnUiThread { lifecycle.destroy() }
    }

    @Test
    fun fullLicenseCanBeOpenedAndClosedWithoutNetwork() {
        compose.setContent {
            contentView = LocalView.current
            val component =
                androidx.compose.runtime.remember {
                    lifecycle.resume()
                    AboutComponent(DefaultComponentContext(lifecycle))
                }
            WhereMeTheme(dark = true) {
                Scaffold(topBar = { ScreenToolbar(Screen.About(component)) }) { padding ->
                    AboutScreen(
                        component,
                        "whereMe",
                        "test",
                        R.raw.aboutlibraries,
                        Modifier.padding(padding),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Написать разработчику").performScrollTo()
        snapshot("about-dark")
        compose.onNodeWithText("Библиотеки и лицензии").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Activity", substring = false))
        snapshot("licenses-dark")
        compose.onNodeWithText("Activity", substring = false).performClick()
        compose.onNodeWithText("TERMS AND CONDITIONS", substring = true).assertExists()
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Версия test").assertExists()
    }

    private fun snapshot(name: String) {
        compose.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(Canvas(bitmap))
            val file = File("build/reports/ui/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
