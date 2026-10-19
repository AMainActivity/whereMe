package ru.ama.whereme.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import ru.ama.whereme.data.di.DataModule
import kotlinx.serialization.json.*
import java.io.IOException
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.repository.NetworkStatus

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val queries = mutableListOf<String>()
    private var fail = false
    private var rows = emptyList<LocationDb>()
    private val history = object : LocationHistoryRepository {
        override fun getLocationById(date: String): Flow<List<LocationDb>> = flow {
            queries.add(date)
            if (fail) throw IOException("unavailable")
            emit(rows)
            awaitCancellation()
        }
    }
    private fun model(saved: SavedStateHandle, cpu: CoroutineDispatcher = dispatcher) = MapViewModel(
        history,
        object : NetworkStatus { override fun isInternetConnected() = false },
        saved, DataModule.provideJson(), cpu
    ).also { models.put("map", it) }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    @Test fun savedDateIsUsedForTheInitialQueryEvenOffline() = runTest(dispatcher) {
        val vm = model(SavedStateHandle(mapOf("selected_date" to "18.10.2026")))
        runCurrent()
        assertEquals(listOf("18.10.2026"), queries)
        assertFalse(vm.isInternetConnected())
        assertEquals("18.10.2026", (vm.locations.value as LoadState.Ready).value.date)
    }

    @Test fun selectingAnotherDateRecoversAfterAQueryFailure() = runTest(dispatcher) {
        fail = true
        val saved = SavedStateHandle(mapOf("selected_date" to "18.10.2026"))
        val vm = model(saved); runCurrent()
        assertEquals(LoadState.Failed, vm.locations.value)
        fail = false
        vm.getDataByDate("19.10.2026"); runCurrent()
        assertEquals("19.10.2026", (vm.locations.value as LoadState.Ready).value.date)
        assertEquals("19.10.2026", saved.get<String>("selected_date"))
    }

    @Test fun jsonPreparationIsDispatchedAndKeepsTheWireFields() = runTest(dispatcher) {
        val cpu = PausedDispatcher()
        rows = listOf(LocationDb(7, "1000", "start", null, "Имя \"тест\"", 55.25, 37.5, 1, 5f, 0f, 0))
        val vm = model(SavedStateHandle(mapOf("selected_date" to "18.10.2026")), cpu)
        runCurrent()
        assertEquals(LoadState.Loading, vm.locations.value)
        assertTrue(cpu.tasks.isNotEmpty())
        cpu.runPending(); runCurrent()
        val day = (vm.locations.value as LoadState.Ready).value
        val point = Json.parseToJsonElement(day.json).jsonArray.single().jsonObject
        assertEquals("18.10.2026", day.date)
        assertEquals(7L, point.getValue("_id").jsonPrimitive.long)
        assertEquals("Имя \"тест\"", point.getValue("info").jsonPrimitive.content)
        assertFalse(point.containsKey("dateend"))
    }

    @Test fun changingDayWhilePreparationIsQueuedCannotPublishTheOldDay() = runTest(dispatcher) {
        val cpu = PausedDispatcher()
        val vm = model(SavedStateHandle(mapOf("selected_date" to "18.10.2026")), cpu)
        runCurrent()
        vm.getDataByDate("19.10.2026"); runCurrent()
        cpu.runPending(); runCurrent()
        cpu.runPending(); runCurrent()
        val day = (vm.locations.value as LoadState.Ready).value
        assertEquals("19.10.2026", day.date)
        assertEquals("[]", day.json)
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runPending() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun explicitRetryReopensTheSameDay() = runTest(dispatcher) {
        fail = true
        val vm = model(SavedStateHandle(mapOf("selected_date" to "18.10.2026"))); runCurrent()
        fail = false
        vm.retryLocations(); runCurrent()
        assertEquals(listOf("18.10.2026", "18.10.2026"), queries)
        assertTrue(vm.locations.value is LoadState.Ready)
    }
}
