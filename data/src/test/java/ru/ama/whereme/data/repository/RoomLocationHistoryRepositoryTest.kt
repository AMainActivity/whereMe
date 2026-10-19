package ru.ama.whereme.data.repository

import java.lang.reflect.Proxy
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.domain.usecase.DayRange

class RoomLocationHistoryRepositoryTest {
    @Test fun historyMappingUsesBackgroundDispatcherAndKeepsDatesOrderAndFields() = runBlocking {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { background ->
                val range = DayRange.parse("19.10.2026")
                val rows = listOf(
                    LocationDbModel("first", range.start, range.start + 1_000, "interval", 1.0, 2.0, 1, 5f, 3f, 0).apply { _id = 4 },
                    LocationDbModel("second", range.start + 2_000, null, null, 3.0, 4.0, 1, 7f, 0f, 1).apply { _id = 9 })
                val dao = Proxy.newProxyInstance(LocationDao::class.java.classLoader, arrayOf(LocationDao::class.java)) { _, method, args ->
                    check(method.name == "getLocations")
                    assertEquals(range.start, args[0])
                    assertEquals(range.end, args[1])
                    flow {
                        assertSame(background, currentCoroutineContext()[ContinuationInterceptor])
                        emit(rows)
                    }
                } as LocationDao
                val result = RoomLocationHistoryRepository(dao, WmMapper(), background).getLocationById("19.10.2026").first()
                assertEquals(listOf(4L, 9L), result.map { it._id })
                assertEquals("19.10.2026 00:00:00", result[0].datestart)
                assertEquals("19.10.2026 00:00:01", result[0].dateend)
                assertEquals("19.10.2026 00:00:02", result[1].datestart)
                assertNull(result[1].dateend)
                assertNull(result[1].info)
                assertEquals(1, result[1].isWrite)
                assertEquals(5f, result[0].accuracy)
                assertEquals(3f, result[0].velocity)
                assertEquals(1.0, result[0].latitude, 0.0)
                assertEquals(2.0, result[0].longitude, 0.0)
            }
        } finally { TimeZone.setDefault(zone) }
    }
}
