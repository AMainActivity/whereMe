package ru.ama.whereme.feature.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.state.LoadState
import ru.ama.whereme.domain.tracking.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycle = LifecycleRegistry()
    private var readFailure = false
    private var subscriptions = 0
    private var active = 0

    private fun <T> stored(value: T) = flow {
        subscriptions++
        if (readFailure) throw IOException("storage unavailable")
        active++
        try {
            emit(value)
            awaitCancellation()
        } finally {
            active--
        }
    }

    private val repository =
        object : SettingsRepository {
            override val settings = stored(SettingsDomModel.DEFAULT)
            override val themeMode = stored(ThemeMode.DARK)

            override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel) =
                error("Unexpected write")

            override suspend fun setThemeMode(mode: ThemeMode) = error("Unexpected write")
        }
    private val control =
        object : TrackingControl {
            override val state = MutableStateFlow<TrackingState>(TrackingState.Stopped)

            override fun capabilities() = TrackingCapabilities(true, true, true, false, true, true)

            override fun start() = Unit

            override fun stop() = Unit

            override suspend fun setSchedule(enabled: Boolean) = Unit

            override suspend fun refreshSchedule() = Unit
        }

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
        lifecycle.resume()
    }

    @After
    fun after() {
        lifecycle.destroy()
        Dispatchers.resetMain()
    }

    @Test
    fun retryResubscribesAfterStorageFailureWithoutInventingSettings() =
        runTest(dispatcher) {
            readFailure = true
            val component =
                SettingsComponent(
                    DefaultComponentContext(lifecycle),
                    repository,
                    control,
                    backgroundScope,
                )
            runCurrent()
            assertEquals(LoadState.Failed, component.state.value.settings)
            assertNull(component.state.value.draft)
            assertEquals(2, subscriptions)
            readFailure = false
            component.accept(SettingsIntent.Retry)
            runCurrent()
            assertEquals(LoadState.Ready(SettingsDomModel.DEFAULT), component.state.value.settings)
            assertEquals(
                MeasurementDraft.from(SettingsDomModel.DEFAULT),
                component.state.value.draft,
            )
            assertEquals(ThemeMode.DARK, component.state.value.theme)
            assertNull(component.state.value.error)
            assertEquals(4, subscriptions)
        }

    @Test
    fun destroyingComponentCancelsBothStorageObservations() =
        runTest(dispatcher) {
            SettingsComponent(
                DefaultComponentContext(lifecycle),
                repository,
                control,
                backgroundScope,
            )
            runCurrent()
            assertEquals(2, active)
            lifecycle.destroy()
            runCurrent()
            assertEquals(0, active)
        }
}
