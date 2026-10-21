package ru.ama.whereme.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
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
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.tracking.*
import ru.ama.whereme.feature.settings.*
import ru.ama.whereme.ui.theme.WhereMeTheme

/**
 * Exercises the production launchers and callbacks; only the external Android response is supplied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackingAccessRequestTest {
    @get:Rule val compose = createComposeRule()
    private val restoration = StateRestorationTester(compose)
    private val lifecycle = LifecycleRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var component: SettingsComponent
    private var capabilities = TrackingCapabilities(true, true, true, false, true, true)
    private var starts = 0
    private var schedules = 0
    private val repository =
        object : SettingsRepository {
            override val settings = MutableStateFlow(SettingsDomModel.DEFAULT)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

            override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel) =
                transform(settings.value).also { settings.value = it }

            override suspend fun setThemeMode(mode: ThemeMode) {
                themeMode.value = mode
            }
        }
    private val control =
        object : TrackingControl {
            override val state = MutableStateFlow<TrackingState>(TrackingState.Stopped)

            override fun capabilities() = capabilities

            override fun start() {
                starts++
            }

            override fun stop() = Unit

            override suspend fun setSchedule(enabled: Boolean) {
                if (enabled) schedules++
            }

            override suspend fun refreshSchedule() = Unit
        }

    private data class Launch(val code: Int, val intent: Intent)

    private val launches = mutableListOf<Launch>()
    private val registry =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launches +=
                    Launch(
                        requestCode,
                        contract.createIntent(RuntimeEnvironment.getApplication(), input),
                    )
            }
        }
    private val owner =
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }

    @Before
    fun before() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnUiThread {
            lifecycle.resume()
            component =
                SettingsComponent(DefaultComponentContext(lifecycle), repository, control, scope)
        }
    }

    @After
    fun after() {
        compose.runOnUiThread {
            lifecycle.destroy()
            scope.cancel()
        }
    }

    private fun show() {
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                WhereMeTheme { Surface { SettingsScreen(component) } }
            }
        }
    }

    private fun start() = compose.onNodeWithText("Запись местоположений").performClick()

    private fun proceed() = compose.onNodeWithText("Продолжить").performClick()

    private fun permissionResult(granted: Boolean) {
        compose.runOnIdle {
            val request = launches.last()
            val names =
                requireNotNull(
                    request.intent.getStringArrayExtra(
                        "androidx.activity.result.contract.extra.PERMISSIONS"
                    )
                )
            assertTrue(registry.dispatchResult(request.code, names.associateWith { granted }))
        }
        compose.waitForIdle()
    }

    private fun settingsResult() {
        compose.runOnIdle {
            assertTrue(
                registry.dispatchResult(
                    launches.last().code,
                    ActivityResult(Activity.RESULT_OK, null),
                )
            )
        }
        compose.waitForIdle()
    }

    private fun assertSettings(action: String) =
        compose.runOnIdle {
            assertEquals(action, launches.last().intent.action)
            assertEquals(0, starts)
        }

    @Test
    fun firstStartTraversesLocationSettingsBackgroundAndAlarmBeforeStartingOnce() {
        capabilities =
            capabilities.copy(
                foreground = false,
                locationEnabled = false,
                background = false,
                exactAlarm = false,
            )
        show()
        start()
        proceed()
        compose.runOnIdle { capabilities = capabilities.copy(foreground = true) }
        permissionResult(true)
        proceed()
        assertSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        compose.runOnIdle { capabilities = capabilities.copy(locationEnabled = true) }
        settingsResult()
        proceed()
        assertSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        compose.runOnIdle { capabilities = capabilities.copy(background = true) }
        settingsResult()
        proceed()
        assertSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        compose.runOnIdle { capabilities = capabilities.copy(exactAlarm = true) }
        settingsResult()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(4, launches.size)
            assertNull(component.state.value.accessRequest)
            assertNull(component.state.value.error)
        }
    }

    @Test
    fun deniedLocationDoesNotStartAndCanBeRequestedAgain() {
        capabilities = capabilities.copy(foreground = false)
        show()
        start()
        proceed()
        permissionResult(false)
        compose.runOnIdle {
            assertEquals(0, starts)
            assertNotNull(component.state.value.error)
        }
        start()
        proceed()
        compose.runOnIdle { capabilities = capabilities.copy(foreground = true) }
        permissionResult(true)
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(2, launches.size)
        }
    }

    @Test
    fun approximateLocationCanBeUpgradedWhenOnlyGpsIsAvailable() {
        capabilities = capabilities.copy(provider = false, preciseWouldProvideLocation = true)
        show()
        start()
        proceed()
        compose.runOnIdle {
            assertArrayEquals(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                launches
                    .single()
                    .intent
                    .getStringArrayExtra("androidx.activity.result.contract.extra.PERMISSIONS"),
            )
            capabilities = capabilities.copy(provider = true)
        }
        permissionResult(true)
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    @Config(sdk = [29])
    fun android10RequestsBackgroundPermissionDirectly() {
        capabilities = capabilities.copy(background = false)
        show()
        start()
        proceed()
        compose.runOnIdle {
            assertArrayEquals(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                launches
                    .single()
                    .intent
                    .getStringArrayExtra("androidx.activity.result.contract.extra.PERMISSIONS"),
            )
            capabilities = capabilities.copy(background = true)
        }
        permissionResult(true)
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    fun unchangedSystemSettingReportsFailureInsteadOfLooping() {
        capabilities = capabilities.copy(locationEnabled = false)
        show()
        start()
        proceed()
        settingsResult()
        compose.runOnIdle {
            assertEquals(0, starts)
            assertEquals(1, launches.size)
            assertNotNull(component.state.value.error)
            assertNull(component.state.value.accessRequest)
        }
    }

    @Test
    fun declinedNotificationsStillAllowRecording() {
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()
        start()
        compose.runOnIdle {
            assertArrayEquals(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                launches
                    .single()
                    .intent
                    .getStringArrayExtra("androidx.activity.result.contract.extra.PERMISSIONS"),
            )
        }
        permissionResult(false)
        compose.runOnIdle {
            assertEquals(1, starts)
            assertNull(component.state.value.error)
        }
    }

    @Test
    fun restoringUiWhileWaitingDoesNotRelaunchAndStillHandlesResult() {
        capabilities = capabilities.copy(exactAlarm = false)
        show()
        start()
        proceed()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(1, launches.size)
            capabilities = capabilities.copy(exactAlarm = true)
        }
        settingsResult()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, launches.size)
        }
    }

    @Test
    fun cancellingExplanationDoesNotLaunchAnythingOrEnableSchedule() {
        capabilities = capabilities.copy(foreground = false)
        show()
        compose.onNodeWithContentDescription("Расписание").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle {
            assertTrue(launches.isEmpty())
            assertEquals(0, starts)
            assertEquals(0, schedules)
            assertNull(component.state.value.accessRequest)
        }
    }
}
