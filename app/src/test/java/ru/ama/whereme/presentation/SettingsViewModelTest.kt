package ru.ama.whereme.presentation

import android.app.Application
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.awaitCancellation
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.domain.tracking.*
import ru.ama.whereme.tracking.TrackingScheduler
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val writes = mutableListOf<SettingsDomModel>()
    private var fail = false
    private lateinit var coordinator: TrackingCoordinator
    private var recording: RecordingSession? = null
    private var pending: RecordingSession? = null
    private var sourceCalls = 0
    private val noon = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
    private val repository = object : SettingsRepository {
        override val settings = MutableStateFlow(SettingsDomModel.DEFAULT)
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
        override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel): SettingsDomModel {
            if (fail) throw java.io.IOException("disk unavailable")
            return transform(settings.value).also { settings.value = it; writes.add(it) }
        }
    }
    private fun TestScope.model(saved: SavedStateHandle = SavedStateHandle()): SettingsViewModel {
        val clock = object : TrackingClock {
            override fun nowMillis() = noon + testScheduler.currentTime
            override fun elapsedRealtimeNanos() = testScheduler.currentTime * 1_000_000
            override fun timeZone(): TimeZone = TimeZone.getTimeZone("UTC")
        }
        coordinator = TrackingCoordinator(repository,
            object : LocationSource {
                override suspend fun locate(accuracyMeters: Int, timeoutMillis: Long): LocationOutcome {
                    sourceCalls++
                    awaitCancellation()
                }
            },
            LocationRecorder { _, _, _ -> error("Unexpected recording") },
            PendingLocationUploader { error("Unexpected upload") },
            clock, backgroundScope,
            object : RecordingSessionStore {
                override suspend fun read(): RecordingSession? = recording
                override suspend fun write(session: RecordingSession?) { recording = session }
            }, object : TrackingAlarm {
                override fun schedule(session: RecordingSession) { pending = session }
                override fun cancel() { pending = null }
            })
        return SettingsViewModel(repository, TrackingScheduler(RuntimeEnvironment.getApplication(), repository, coordinator, clock),
            saved, coordinator, backgroundScope).also { models.put("settings", it) }
    }
    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun enablingScheduleInsideCurrentIntervalPreparesSamplingAndTheNextDailyStart() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.setSchedule(true); runCurrent()
        assertNull(vm.errorMessage.value)
        val start = requireNotNull(pending)
        assertEquals(TrackingMode.SCHEDULED, start.mode)
        assertEquals(noon + 1_000, start.nextAtMillis)
        val app = RuntimeEnvironment.getApplication()
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), alarms.nextAlarmClock.triggerTime)
        advanceTimeBy(1_000)
        coordinator.attach().wake(start.id, start.nextAtMillis); runCurrent()
        assertEquals(1, sourceCalls)
    }

    @Test fun enablingOutsideIntervalOnlySchedulesTheFutureStart() = runTest(dispatcher) {
        repository.settings.value = repository.settings.value.copy(start = "13:00", end = "17:00")
        val vm = model(); runCurrent()
        vm.setSchedule(true); runCurrent()
        assertTrue(repository.settings.value.isEnable)
        assertNull(pending)
        val alarms = RuntimeEnvironment.getApplication().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(Instant.parse("2026-10-19T13:00:00Z").toEpochMilli(), alarms.nextAlarmClock.triggerTime)
    }

    @Test fun changingEnabledScheduleToIncludeNowPreparesSampling() = runTest(dispatcher) {
        repository.settings.value = repository.settings.value.copy(isEnable = true, start = "13:00", end = "17:00")
        val vm = model(); runCurrent()
        vm.update { it.copy(start = "11:00") }; runCurrent()
        assertEquals(TrackingMode.SCHEDULED, pending?.mode)
        assertEquals(noon + 1_000, pending?.nextAtMillis)
    }

    @Test fun repeatedEnableKeepsThePreparedRecordingAndDeadline() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.setSchedule(true); runCurrent()
        val start = requireNotNull(pending)
        advanceTimeBy(500)
        vm.setSchedule(true); runCurrent()
        assertEquals(start, pending)
    }

    @Test fun disablingBeforeSamplingAlarmPreventsThePreparedStart() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.setSchedule(true); runCurrent()
        val start = requireNotNull(pending)
        vm.setSchedule(false); runCurrent()
        assertNull(pending)
        assertNull(recording)
        advanceTimeBy(1_000)
        coordinator.attach().wake(start.id, start.nextAtMillis); runCurrent()
        assertEquals(0, sourceCalls)
    }

    @Test fun enablingAndDisablingScheduleDoesNotRestartManualMeasurement() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        coordinator.attach().start(TrackingMode.MANUAL); runCurrent()
        val start = requireNotNull(pending)
        vm.setSchedule(true); runCurrent()
        vm.setSchedule(false); runCurrent()
        assertEquals(start, pending)
        assertEquals(TrackingMode.MANUAL, recording?.mode)
        assertEquals(1, sourceCalls)
    }

    @Test fun missingLocationPermissionDoesNotEnableSchedule() = runTest(dispatcher) {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val vm = model(); runCurrent()
        vm.setSchedule(true); runCurrent()
        assertFalse(repository.settings.value.isEnable)
        assertNull(pending)
        assertNotNull(vm.errorMessage.value)
    }

    @Test fun typingChangesNoPersistedSettingsUntilApply() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.editMeasurement(MeasurementField.PERIOD, "15")
        vm.editMeasurement(MeasurementField.PERIOD, "150"); runCurrent()
        assertTrue(writes.isEmpty())
        vm.applyMeasurements(); vm.applyMeasurements(); runCurrent()
        assertEquals(listOf(150), writes.map { it.timeOfWorkingWM })
    }
    @Test fun failedWriteKeepsDraftForExplicitRetry() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.editMeasurement(MeasurementField.PERIOD, "150")
        fail = true; vm.applyMeasurements(); runCurrent()
        assertEquals("150", vm.draft.value?.period)
        assertEquals(100, repository.settings.value.timeOfWorkingWM)
        assertNotNull(vm.errorMessage.value)
        assertFalse(vm.isSaving.value)
        fail = false; vm.applyMeasurements(); runCurrent()
        assertEquals(150, repository.settings.value.timeOfWorkingWM)
        assertNull(vm.errorMessage.value)
    }
    @Test fun invalidDraftCannotBeSavedAndSurvivesRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = model(saved); runCurrent()
        vm.editMeasurement(MeasurementField.PERIOD, "")
        vm.applyMeasurements(); runCurrent()
        val recreated = model(saved); runCurrent()
        assertEquals("", recreated.draft.value?.period)
        assertTrue(writes.isEmpty())
    }
    @Test fun applyingNumbersPreservesNewScheduleValues() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.editMeasurement(MeasurementField.PERIOD, "150")
        repository.settings.value = repository.settings.value.copy(start = "10:00")
        vm.applyMeasurements(); runCurrent()
        assertEquals("10:00", repository.settings.value.start)
        assertEquals(150, repository.settings.value.timeOfWorkingWM)
    }
}
