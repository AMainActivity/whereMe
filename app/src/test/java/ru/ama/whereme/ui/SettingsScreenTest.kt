package ru.ama.whereme.ui

import java.time.LocalTime
import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.tracking.*
import ru.ama.whereme.feature.settings.SettingsComponent
import ru.ama.whereme.feature.settings.SettingsScreen
import ru.ama.whereme.navigation.Screen
import ru.ama.whereme.ui.theme.WhereMeTheme
import ru.ama.whereme.ui.theme.isDark

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h873dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()
    private lateinit var component: SettingsComponent
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var contentView: View
    private var writes = 0
    private var starts = 0
    private var stops = 0
    private val repository =
        object : SettingsRepository {
            override val settings = MutableStateFlow(SettingsDomModel.DEFAULT)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

            override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel) =
                transform(settings.value).also {
                    writes++
                    settings.value = it
                }

            override suspend fun setThemeMode(mode: ThemeMode) {
                themeMode.value = mode
            }
        }
    private val tracking =
        object : TrackingControl {
            override val state = MutableStateFlow<TrackingState>(TrackingState.Stopped)

            override fun capabilities() = TrackingCapabilities(true, true, true, false, true, true)

            override suspend fun setSchedule(enabled: Boolean) = Unit

            override suspend fun refreshSchedule() = Unit

            override fun start() {
                starts++
                state.value = TrackingState.Starting(TrackingMode.MANUAL)
            }

            override fun stop() {
                stops++
                state.value = TrackingState.Stopped
            }
        }

    @Before
    fun before() {
        compose.runOnUiThread {
            lifecycle.resume()
            component =
                SettingsComponent(
                    DefaultComponentContext(lifecycle),
                    repository,
                    tracking,
                    applicationScope,
                )
        }
    }

    @After
    fun after() {
        compose.runOnUiThread {
            lifecycle.destroy()
            applicationScope.cancel()
        }
    }

    private fun show(dark: Boolean, fontScale: Float = 1f) {
        repository.themeMode.value = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
        compose.setContent {
            contentView = LocalView.current
            val mode by repository.themeMode.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WhereMeTheme(mode.isDark()) {
                    Scaffold(topBar = { ScreenToolbar(Screen.Settings(component)) }) { padding ->
                        SettingsScreen(component, Modifier.padding(padding))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun invalidInputDisablesApplyUntilCorrected() {
        show(false)
        compose
            .onNodeWithText("Период между замерами, с")
            .performScrollTo()
            .performTextReplacement("")
        compose.onNodeWithText("Применить параметры").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Целое число, не меньше 15").assertExists()
        compose
            .onNodeWithText("Период между замерами, с")
            .performScrollTo()
            .performTextReplacement("150")
        compose
            .onNodeWithText("Применить параметры")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals(150, repository.settings.value.timeOfWorkingWM)
            assertEquals(1, writes)
        }
    }

    @Test
    fun themeChoiceIsWrittenWithoutChangingSchedule() {
        show(false)
        compose.onNodeWithText("Тёмная").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, repository.themeMode.value)
            assertFalse(repository.settings.value.isEnable)
        }
        compose.onNodeWithText("Тёмная").assertIsSelected()
    }

    @Test
    fun lightScreenRendersWithLargeText() {
        show(false, 1.3f)
        compose.onNodeWithText("Запись местоположений").assertIsDisplayed()
        snapshot("settings-light")
        compose.onNodeWithText("Тёмная").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun darkScreenRendersWithLargeText() {
        show(true, 1.3f)
        compose.onNodeWithText("Запись местоположений").assertIsDisplayed()
        snapshot("settings-dark")
        compose.onNodeWithText("Как на устройстве").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recordingSwitchStartsAndStopsWithoutEnablingSchedule() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(false)
        compose.onNodeWithText("Запись местоположений").assertIsOff().performClick()
        compose.onNodeWithText("Запись местоположений").assertIsOn().performClick()
        compose.onNodeWithText("Запись местоположений").assertIsOff()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, stops)
            assertFalse(repository.settings.value.isEnable)
        }
    }

    @Test
    fun timeCanBeEditedWithKeyboardAndDialBeforeSaving() {
        show(false)
        compose.onNodeWithText("Начало:", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ввести время вручную").performClick()
        val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        inputs[0].performTextReplacement("09")
        inputs[1].performTextReplacement("45")
        compose.onNodeWithContentDescription("Выбрать на часах").performClick()
        compose.onNodeWithContentDescription("Ввести время вручную").assertIsDisplayed()
        compose.onNodeWithText("Готово").performClick()
        compose.runOnIdle {
            assertEquals(LocalTime.parse("09:45"), repository.settings.value.start)
            assertEquals(1, writes)
        }
    }

    @Test
    fun cancellingTimePickerDoesNotChangeSchedule() {
        show(true)
        val before = repository.settings.value
        compose.onNodeWithText("Окончание:", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ввести время вручную").performClick()
        compose
            .onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0]
            .performTextReplacement("22")
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle {
            assertEquals(before, repository.settings.value)
            assertEquals(0, writes)
        }
    }

    private fun snapshot(name: String) {
        // PixelCopy needs a real Android window. Robolectric draws the Compose view to a native
        // Canvas instead.
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
