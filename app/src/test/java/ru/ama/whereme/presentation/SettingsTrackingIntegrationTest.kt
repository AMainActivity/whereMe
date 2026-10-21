package ru.ama.whereme.presentation

import java.time.ZoneId
import java.time.LocalTime
import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.location.LocationManager
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.domain.tracking.*
import ru.ama.whereme.feature.settings.*
import ru.ama.whereme.tracking.AndroidTrackingControl
import ru.ama.whereme.tracking.TrackingScheduler

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTrackingIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val writes = mutableListOf<SettingsDomModel>()
    private var fail = false
    private lateinit var scheduler: TrackingScheduler
    private lateinit var coordinator: TrackingCoordinator
    private var recording: RecordingSession? = null
    private var pending: RecordingSession? = null
    private var sourceCalls = 0
    private val noon = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
    private val repository =
        object : SettingsRepository {
            override val settings = MutableStateFlow(SettingsDomModel.DEFAULT)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

            override suspend fun setThemeMode(mode: ThemeMode) {
                themeMode.value = mode
            }

            override suspend fun updateSettings(
                transform: (SettingsDomModel) -> SettingsDomModel
            ): SettingsDomModel {
                if (fail) throw java.io.IOException("disk unavailable")
                return transform(settings.value).also {
                    settings.value = it
                    writes.add(it)
                }
            }
        }

    private fun TestScope.model(
        saved: StateKeeperDispatcher = StateKeeperDispatcher()
    ): SettingsComponent {
        val clock =
            object : TrackingClock {
                override fun now() = Instant.ofEpochMilli(noon + testScheduler.currentTime)

                override fun elapsedRealtimeNanos() = testScheduler.currentTime * 1_000_000

                override fun zoneId(): ZoneId = ZoneId.of("UTC")
            }
        coordinator =
            TrackingCoordinator(
                repository,
                object : LocationSource {
                    override suspend fun locate(
                        accuracyMeters: Int,
                        timeoutMillis: Long,
                    ): LocationOutcome {
                        sourceCalls++
                        awaitCancellation()
                    }
                },
                LocationRecorder { _, _, _ -> error("Unexpected recording") },
                PendingLocationUploader { error("Unexpected upload") },
                clock,
                backgroundScope,
                object : RecordingSessionStore {
                    override suspend fun read(): RecordingSession? = recording

                    override suspend fun write(session: RecordingSession?) {
                        recording = session
                    }
                },
                object : TrackingAlarm {
                    override fun schedule(session: RecordingSession) {
                        pending = session
                    }

                    override fun cancel() {
                        pending = null
                    }
                },
            )
        val lifecycle =
            LifecycleRegistry().also {
                it.resume()
                lifecycles.add(it)
            }
        val app = RuntimeEnvironment.getApplication()
        scheduler = TrackingScheduler(app, repository, coordinator, clock)
        return SettingsComponent(
            DefaultComponentContext(lifecycle, stateKeeper = saved),
            repository,
            AndroidTrackingControl(
                app,
                coordinator,
                scheduler,
            ),
            backgroundScope,
        )
    }

    private fun SettingsComponent.setSchedule(enabled: Boolean) {
        accept(SettingsIntent.Schedule(enabled))
        if (enabled) accept(SettingsIntent.AccessFinished())
    }

    @Test
    fun dailyAlarmStartsServiceAtPeriodBeginningAndArmsNextSelectedDay() = runTest(dispatcher) {
        model()
        repository.settings.value = SettingsDomModel.DEFAULT.copy(
            isEnable = true, start = LocalTime.parse("12:01"), end = LocalTime.parse("13:00"),
            days = listOf("1", "0", "1", "0", "0", "0", "0"),
        )
        val app = RuntimeEnvironment.getApplication()
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduler.scheduleNextAlarmClockIfEnabled()
        assertEquals(noon + 60_000, alarms.nextAlarmClock.triggerTime)
        val receiver = ru.ama.whereme.data.alarms.AlarmClockStart().apply { repo = scheduler }
        advanceTimeBy(60_000)
        receiver.startScheduledTracking(app)
        val started = requireNotNull(shadowOf(app).nextStartedService)
        assertEquals(MyForegroundService::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra("scheduled", false))
        assertEquals(Instant.parse("2026-10-21T12:01:00Z").toEpochMilli(), alarms.nextAlarmClock.triggerTime)
        coordinator.attach().start(TrackingMode.SCHEDULED)
        runCurrent()
        assertEquals(1, sourceCalls)
    }

    @Test
    fun lateDailyAlarmAfterPeriodEndDoesNotStartServiceButKeepsNextDay() = runTest(dispatcher) {
        model()
        repository.settings.value = SettingsDomModel.DEFAULT.copy(isEnable = true, start = LocalTime.parse("11:00"), end = LocalTime.parse("12:00"))
        val app = RuntimeEnvironment.getApplication()
        ru.ama.whereme.data.alarms.AlarmClockStart().apply { repo = scheduler }.startScheduledTracking(app)
        assertNull(shadowOf(app).nextStartedService)
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(Instant.parse("2026-10-20T11:00:00Z").toEpochMilli(), alarms.nextAlarmClock.triggerTime)
    }

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @After
    fun after() {
        lifecycles.forEach { it.destroy() }
        Dispatchers.resetMain()
    }

    @Test
    fun enablingScheduleInsideCurrentIntervalPreparesSamplingAndTheNextDailyStart() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.setSchedule(true)
            runCurrent()
            assertNull(vm.state.value.error)
            val start = requireNotNull(pending)
            assertEquals(TrackingMode.SCHEDULED, start.mode)
            assertEquals(Instant.ofEpochMilli(noon + 1_000), start.nextAt)
            val app = RuntimeEnvironment.getApplication()
            val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            assertEquals(
                Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(),
                alarms.nextAlarmClock.triggerTime,
            )
            advanceTimeBy(1_000)
            coordinator.attach().wake(start.id, start.nextAt)
            runCurrent()
            assertEquals(1, sourceCalls)
        }

    @Test
    fun enablingOutsideIntervalOnlySchedulesTheFutureStart() =
        runTest(dispatcher) {
            repository.settings.value =
                repository.settings.value.copy(start = LocalTime.parse("13:00"), end = LocalTime.parse("17:00"))
            val vm = model()
            runCurrent()
            vm.setSchedule(true)
            runCurrent()
            assertTrue(repository.settings.value.isEnable)
            assertNull(pending)
            val alarms =
                RuntimeEnvironment.getApplication().getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager
            assertEquals(
                Instant.parse("2026-10-19T13:00:00Z").toEpochMilli(),
                alarms.nextAlarmClock.triggerTime,
            )
        }

    @Test
    fun changingEnabledScheduleToIncludeNowPreparesSampling() =
        runTest(dispatcher) {
            repository.settings.value =
                repository.settings.value.copy(isEnable = true, start = LocalTime.parse("13:00"), end = LocalTime.parse("17:00"))
            val vm = model()
            runCurrent()
            vm.accept(SettingsIntent.Time(true, LocalTime.parse("11:00")))
            runCurrent()
            assertEquals(TrackingMode.SCHEDULED, pending?.mode)
            assertEquals(Instant.ofEpochMilli(noon + 1_000), pending?.nextAt)
        }

    @Test
    fun repeatedEnableKeepsThePreparedRecordingAndDeadline() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.setSchedule(true)
            runCurrent()
            val start = requireNotNull(pending)
            advanceTimeBy(500)
            vm.setSchedule(true)
            runCurrent()
            assertEquals(start, pending)
        }

    @Test
    fun disablingBeforeSamplingAlarmPreventsThePreparedStart() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.setSchedule(true)
            runCurrent()
            val start = requireNotNull(pending)
            vm.setSchedule(false)
            runCurrent()
            assertNull(pending)
            assertNull(recording)
            advanceTimeBy(1_000)
            coordinator.attach().wake(start.id, start.nextAt)
            runCurrent()
            assertEquals(0, sourceCalls)
        }

    @Test
    fun enablingAndDisablingScheduleReconfiguresManualMeasurement() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            coordinator.attach().start(TrackingMode.MANUAL)
            runCurrent()
            val start = requireNotNull(pending)
            vm.setSchedule(true)
            runCurrent()
            vm.setSchedule(false)
            runCurrent()
            assertEquals(start, pending)
            assertEquals(TrackingMode.MANUAL, recording?.mode)
            assertEquals(3, sourceCalls)
        }

    @Test
    fun missingLocationPermissionDoesNotEnableSchedule() =
        runTest(dispatcher) {
            shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            val vm = model()
            runCurrent()
            vm.setSchedule(true)
            runCurrent()
            assertFalse(repository.settings.value.isEnable)
            assertNull(pending)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun typingChangesNoPersistedSettingsUntilApply() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.accept(SettingsIntent.Edit(MeasurementField.PERIOD, "15"))
            vm.accept(SettingsIntent.Edit(MeasurementField.PERIOD, "150"))
            runCurrent()
            assertTrue(writes.isEmpty())
            vm.accept(SettingsIntent.Apply)
            vm.accept(SettingsIntent.Apply)
            runCurrent()
            assertEquals(listOf(150), writes.map { it.timeOfWorkingWM })
        }

    @Test
    fun failedWriteKeepsDraftForExplicitRetry() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.accept(SettingsIntent.Edit(MeasurementField.PERIOD, "150"))
            fail = true
            vm.accept(SettingsIntent.Apply)
            runCurrent()
            assertEquals("150", vm.state.value.draft?.period)
            assertEquals(100, repository.settings.value.timeOfWorkingWM)
            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.saving)
            fail = false
            vm.accept(SettingsIntent.Apply)
            runCurrent()
            assertEquals(150, repository.settings.value.timeOfWorkingWM)
            assertNull(vm.state.value.error)
        }

    @Test
    fun invalidDraftCannotBeSavedAndSurvivesRecreation() =
        runTest(dispatcher) {
            val saved = StateKeeperDispatcher()
            val vm = model(saved)
            runCurrent()
            vm.accept(SettingsIntent.Edit(MeasurementField.PERIOD, ""))
            vm.accept(SettingsIntent.Apply)
            runCurrent()
            val recreated = model(StateKeeperDispatcher(saved.save()))
            runCurrent()
            assertEquals("", recreated.state.value.draft?.period)
            assertTrue(writes.isEmpty())
        }

    @Test
    fun pendingActionSurvivesComponentRestoration() =
        runTest(dispatcher) {
            for (action in TrackingAction.entries) {
                val saved = StateKeeperDispatcher()
                val original = model(saved)
                runCurrent()
                original.accept(
                    if (action == TrackingAction.MANUAL) SettingsIntent.Start
                    else SettingsIntent.Schedule(true)
                )
                val restored = model(StateKeeperDispatcher(saved.save()))
                runCurrent()
                assertEquals(action, restored.state.value.accessRequest)
                assertFalse(repository.settings.value.isEnable)
            }
        }

    @Test
    fun applyingNumbersPreservesNewScheduleValues() =
        runTest(dispatcher) {
            val vm = model()
            runCurrent()
            vm.accept(SettingsIntent.Edit(MeasurementField.PERIOD, "150"))
            repository.settings.value = repository.settings.value.copy(start = LocalTime.parse("10:00"))
            vm.accept(SettingsIntent.Apply)
            runCurrent()
            assertEquals(LocalTime.parse("10:00"), repository.settings.value.start)
            assertEquals(150, repository.settings.value.timeOfWorkingWM)
        }
}
