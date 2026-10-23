package ru.ama.whereme.feature.map

import java.time.Instant
import java.time.LocalDate
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.LocationOutcome
import ru.ama.whereme.domain.entity.LocationSample
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.state.LoadState

@OptIn(ExperimentalCoroutinesApi::class)
class MapComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val queries = mutableListOf<String>()
    private var activeQueries = 0
    private var fail = false
    private var rows = emptyList<LocationDb>()
    private val history =
        object : LocationHistoryRepository {
            override val lastRecordedTime = kotlinx.coroutines.flow.flowOf<Instant?>(null)
            override fun getLocationById(date: LocalDate): Flow<List<LocationDb>> = flow {
                queries.add(date.format(MAP_DATE_FORMAT))
                if (fail) throw IOException("unavailable")
                activeQueries++
                try {
                    emit(rows)
                    awaitCancellation()
                } finally {
                    activeQueries--
                }
            }
        }

    private fun savedDate(): StateKeeperDispatcher {
        val saved = StateKeeperDispatcher()
        saved.register("selected_date", String.serializer()) { "18.10.2026" }
        return StateKeeperDispatcher(saved.save())
    }

    private fun model(
        saved: StateKeeperDispatcher,
        cpu: CoroutineDispatcher = dispatcher,
        visible: Boolean = true,
        locate: suspend () -> LocationOutcome = { error("Location not requested in this test") },
    ): MapComponent {
        val lifecycle =
            LifecycleRegistry().also {
                if (visible) it.resume() else it.create()
                lifecycles.add(it)
            }
        return MapComponent(DefaultComponentContext(lifecycle, stateKeeper = saved), history, locate, cpu)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        lifecycles.forEach { it.destroy() }
        Dispatchers.resetMain()
    }

    @Test
    fun hiddenTabDoesNotQueryHistoryUntilItBecomesVisible() =
        runTest(dispatcher) {
            val component = model(savedDate(), visible = false)
            runCurrent()
            assertTrue(queries.isEmpty())
            lifecycles.last().resume()
            runCurrent()
            assertEquals(listOf("18.10.2026"), queries)
            assertTrue(component.state.value.locations is LoadState.Ready)
        }

    @Test
    fun hidingMapCancelsObservationAndReturningReadsCurrentData() =
        runTest(dispatcher) {
            val component = model(savedDate())
            runCurrent()
            assertEquals(1, activeQueries)
            lifecycles.last().stop()
            runCurrent()
            assertEquals(0, activeQueries)
            rows = listOf(LocationDb(9, "1000", Instant.ofEpochMilli(1000), null, null, 55.25, 37.5, 1, 5f, 0f, 0))
            lifecycles.last().resume()
            runCurrent()
            assertEquals(listOf("18.10.2026", "18.10.2026"), queries)
            val points =
                Json.parseToJsonElement((component.state.value.locations as LoadState.Ready).value)
                    .jsonArray
            assertEquals(9L, points.single().jsonObject.getValue("_id").jsonPrimitive.long)
        }

    @Test
    fun savedDateIsUsedForTheInitialQueryEvenOffline() =
        runTest(dispatcher) {
            val vm = model(savedDate())
            runCurrent()
            assertEquals(listOf("18.10.2026"), queries)
            assertEquals(LocalDate.parse("2026-10-18"), vm.state.value.date)
        }

    @Test
    fun selectingAnotherDateRecoversAfterAQueryFailure() =
        runTest(dispatcher) {
            fail = true
            val saved = savedDate()
            val vm = model(saved)
            runCurrent()
            assertEquals(LoadState.Failed, vm.state.value.locations)
            fail = false
            vm.accept(MapIntent.SelectDate(LocalDate.parse("2026-10-19")))
            runCurrent()
            assertEquals(LocalDate.parse("2026-10-19"), vm.state.value.date)
            assertEquals(
                "19.10.2026",
                StateKeeperDispatcher(saved.save()).consume("selected_date", String.serializer()),
            )
        }

    @Test
    fun jsonPreparationIsDispatchedAndContainsOnlyMapFields() =
        runTest(dispatcher) {
            val cpu = PausedDispatcher()
            rows =
                listOf(
                    LocationDb(7, "1000", Instant.ofEpochMilli(1000), null, "Имя \"тест\"", 55.25, 37.5, 1, 5f, 0f, 0)
                )
            val vm = model(savedDate(), cpu)
            runCurrent()
            assertEquals(LoadState.Loading, vm.state.value.locations)
            assertTrue(cpu.tasks.isNotEmpty())
            while (cpu.tasks.isNotEmpty()) {
                cpu.runPending()
                runCurrent()
            }
            val day = vm.state.value
            val point =
                Json.parseToJsonElement((day.locations as LoadState.Ready).value)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(LocalDate.parse("2026-10-18"), day.date)
            assertEquals(7L, point.getValue("_id").jsonPrimitive.long)
            assertEquals(setOf("_id", "datestart", "latitude", "longitude", "accuracy"), point.keys)
        }

    @Test
    fun changingDayWhilePreparationIsQueuedCannotPublishTheOldDay() =
        runTest(dispatcher) {
            val cpu = PausedDispatcher()
            val vm = model(savedDate(), cpu)
            runCurrent()
            vm.accept(MapIntent.SelectDate(LocalDate.parse("2026-10-19")))
            runCurrent()
            while (cpu.tasks.isNotEmpty()) {
                cpu.runPending()
                runCurrent()
            }
            val day = vm.state.value
            assertEquals(LocalDate.parse("2026-10-19"), day.date)
            assertEquals("[]", (day.locations as LoadState.Ready).value)
        }

    @Test
    fun layerVisibilityIsIndependentAndSurvivesRecreation() = runTest(dispatcher) {
        val saved = savedDate()
        val component = model(saved)
        runCurrent()
        component.accept(MapIntent.PreviousPoints(false))
        assertEquals(MapLayers(previousPointsVisible = false, trackVisible = true), component.state.value.layers)
        component.accept(MapIntent.Track(false))
        val restored = model(StateKeeperDispatcher(saved.save()))
        runCurrent()
        assertEquals(MapLayers(false, false), restored.state.value.layers)
        assertEquals(LocalDate.parse("2026-10-18"), restored.state.value.date)
    }

    @Test
    fun dayUpdatesAndRetriesPreserveLayerChoicesWithoutQueryingForToggles() = runTest(dispatcher) {
        val component = model(savedDate())
        runCurrent()
        component.accept(MapIntent.PreviousPoints(false))
        component.accept(MapIntent.PreviousPoints(false))
        runCurrent()
        assertEquals(1, queries.size)
        component.accept(MapIntent.SelectDate(LocalDate.parse("2026-10-19")))
        runCurrent()
        assertFalse(component.state.value.layers.previousPointsVisible)
        component.accept(MapIntent.Retry)
        runCurrent()
        assertEquals(MapLayers(false, true), component.state.value.layers)
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runPending() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    @Test
    fun explicitRetryReopensTheSameDay() =
        runTest(dispatcher) {
            fail = true
            val vm = model(savedDate())
            runCurrent()
            fail = false
            vm.accept(MapIntent.Retry)
            runCurrent()
            assertEquals(listOf("18.10.2026", "18.10.2026"), queries)
            assertTrue(vm.state.value.locations is LoadState.Ready)
        }

    @Test
    fun permissionWaitKeepsTheRequestedRouteAndStartsOnlyAfterGrant() = runTest(dispatcher) {
        var requests = 0
        val component = model(savedDate(), locate = { requests++; LocationOutcome.Fix(sample) })
        val action = MapLocationAction.Route(56.0, 38.0)
        component.accept(MapIntent.RequestLocation(action, permissionGranted = false))
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = false))
        lifecycles.last().pause()
        runCurrent()
        assertEquals(0, requests)
        assertEquals(MapLocationState.AwaitingPermission(action), component.state.value.location)
        lifecycles.last().resume()
        component.accept(MapIntent.LocationPermissionResult(true))
        component.accept(MapIntent.LocationPermissionResult(true))
        runCurrent()
        assertEquals(1, requests)
        val route = (component.state.value.location as MapLocationState.Route).request
        assertEquals(sample.latitude, route.fromLatitude, 0.0)
        assertEquals(sample.longitude, route.fromLongitude, 0.0)
        assertEquals(56.0, route.toLatitude, 0.0)
        assertEquals(38.0, route.toLongitude, 0.0)
        component.accept(MapIntent.DismissLocation)
        component.accept(MapIntent.LocationPermissionResult(true))
        runCurrent()
        assertEquals(MapLocationState.Idle, component.state.value.location)
        assertEquals(1, requests)
    }

    @Test
    fun permissionDenialDoesNotStartLocationAndAUserRetryCanRecover() = runTest(dispatcher) {
        val component = model(savedDate(), locate = { LocationOutcome.Fix(sample) })
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = false))
        component.accept(MapIntent.LocationPermissionResult(false))
        runCurrent()
        assertEquals(MapLocationState.Failed(LocationOutcome.Reason.PERMISSION_REQUIRED), component.state.value.location)
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = true))
        runCurrent()
        assertEquals(MapLocationState.Position(sample), component.state.value.location)
    }

    @Test
    fun historyRefreshDoesNotResetAnActiveLocationRequestAndDoubleTapDoesNotRestartIt() = runTest(dispatcher) {
        var requests = 0
        val result = CompletableDeferred<LocationOutcome>()
        val component = model(savedDate(), locate = { requests++; result.await() })
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = true))
        runCurrent()
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = true))
        component.accept(MapIntent.Retry)
        runCurrent()
        assertEquals(1, requests)
        assertEquals(MapLocationState.Locating, component.state.value.location)
        result.complete(LocationOutcome.Unavailable(LocationOutcome.Reason.LOCATION_DISABLED))
        runCurrent()
        assertEquals(MapLocationState.Failed(LocationOutcome.Reason.LOCATION_DISABLED), component.state.value.location)
    }

    @Test
    fun leavingTheScreenCancelsLocationWithoutRestartingItOnReturn() = runTest(dispatcher) {
        var requests = 0
        var cancellations = 0
        val component = model(savedDate(), locate = {
            requests++
            try { awaitCancellation() } finally { cancellations++ }
        })
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = true))
        runCurrent()
        lifecycles.last().pause()
        runCurrent()
        assertEquals(1, cancellations)
        assertEquals(MapLocationState.Idle, component.state.value.location)
        lifecycles.last().resume()
        runCurrent()
        assertEquals(1, requests)
        assertEquals(MapLocationState.Idle, component.state.value.location)
    }

    @Test
    fun destroyingTheComponentCancelsTheProviderRequest() = runTest(dispatcher) {
        var cancelled = false
        val component = model(savedDate(), locate = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, permissionGranted = true))
        runCurrent()
        lifecycles.last().destroy()
        runCurrent()
        assertTrue(cancelled)
    }

    @Test
    fun settingsReadFailureIsVisibleAndNextRequestCanRecover() = runTest(dispatcher) {
        var unavailable = true
        val component = model(savedDate(), locate = {
            if (unavailable) throw IOException("Settings unavailable")
            LocationOutcome.Fix(sample)
        })
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, true))
        runCurrent()
        assertEquals(MapLocationState.SettingsUnavailable, component.state.value.location)
        unavailable = false
        component.accept(MapIntent.RequestLocation(MapLocationAction.Show, true))
        runCurrent()
        assertEquals(MapLocationState.Position(sample), component.state.value.location)
    }

    @Test
    fun uploadAcknowledgementDoesNotPublishAnotherMapPayload() = runTest(dispatcher) {
        val first = LocationDb(1, "1000", Instant.ofEpochMilli(1000), null, null, 55.0, 37.0, 1, 5f, 0f, 0)
        val updates = kotlinx.coroutines.flow.MutableStateFlow(listOf(first))
        val repository = object : LocationHistoryRepository {
            override val lastRecordedTime = kotlinx.coroutines.flow.flowOf<Instant?>(null)
            override fun getLocationById(date: LocalDate) = updates
        }
        val lifecycle = LifecycleRegistry().also { it.resume(); lifecycles.add(it) }
        val component = MapComponent(DefaultComponentContext(lifecycle), repository,
            locateCurrent = { error("Not requested") }, defaultDispatcher = dispatcher)
        runCurrent()
        val before = component.state.value
        updates.value = listOf(first.copy(isWrite = 1))
        runCurrent()
        assertSame(before, component.state.value)
    }

    private val sample = LocationSample(55.0, 37.0, 5f, 0f, Instant.ofEpochMilli(1000), 1000L)
}
