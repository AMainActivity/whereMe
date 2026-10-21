package ru.ama.whereme.feature.map

import java.time.Instant
import java.time.LocalDate
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
    ): MapComponent {
        val lifecycle =
            LifecycleRegistry().also {
                if (visible) it.resume() else it.create()
                lifecycles.add(it)
            }
        return MapComponent(DefaultComponentContext(lifecycle, stateKeeper = saved), history, cpu)
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
    fun jsonPreparationIsDispatchedAndKeepsTheWireFields() =
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
            cpu.runPending()
            runCurrent()
            val day = vm.state.value
            val point =
                Json.parseToJsonElement((day.locations as LoadState.Ready).value)
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(LocalDate.parse("2026-10-18"), day.date)
            assertEquals(7L, point.getValue("_id").jsonPrimitive.long)
            assertEquals("Имя \"тест\"", point.getValue("info").jsonPrimitive.content)
            assertFalse(point.containsKey("dateend"))
        }

    @Test
    fun changingDayWhilePreparationIsQueuedCannotPublishTheOldDay() =
        runTest(dispatcher) {
            val cpu = PausedDispatcher()
            val vm = model(savedDate(), cpu)
            runCurrent()
            vm.accept(MapIntent.SelectDate(LocalDate.parse("2026-10-19")))
            runCurrent()
            cpu.runPending()
            runCurrent()
            cpu.runPending()
            runCurrent()
            val day = vm.state.value
            assertEquals(LocalDate.parse("2026-10-19"), day.date)
            assertEquals("[]", (day.locations as LoadState.Ready).value)
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
}
