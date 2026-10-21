package ru.ama.whereme.data.repository

import java.time.Instant
import java.time.LocalDate
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
                val range = DayRange.of(LocalDate.parse("2026-10-19"))
                val rows = listOf(
                    LocationDbModel(range.start.toEpochMilli().toString(), range.start.toEpochMilli(), range.start.toEpochMilli() + 1_000, "interval", 1.0, 2.0, 1, 5f, 3f, 0).apply { _id = 4 },
                    LocationDbModel(range.start.plusMillis(2_000).toEpochMilli().toString(), range.start.toEpochMilli() + 2_000, null, null, 3.0, 4.0, 1, 7f, 0f, 1).apply { _id = 9 })
                val dao = Proxy.newProxyInstance(LocationDao::class.java.classLoader, arrayOf(LocationDao::class.java)) { _, method, args ->
                    check(method.name == "getLocations")
                    assertEquals(range.start.toEpochMilli(), args[0])
                    assertEquals(range.end.toEpochMilli(), args[1])
                    flow {
                        assertSame(background, currentCoroutineContext()[ContinuationInterceptor])
                        emit(rows)
                    }
                } as LocationDao
                val result = RoomLocationHistoryRepository(dao, WmMapper(), background).getLocationById(LocalDate.parse("2026-10-19")).first()
                assertEquals(listOf(4L, 9L), result.map { it._id })
                assertEquals(Instant.parse("2026-10-19T00:00:00Z"), result[0].datestart)
                assertEquals(Instant.parse("2026-10-19T00:00:01Z"), result[0].dateend)
                assertEquals(Instant.parse("2026-10-19T00:00:02Z"), result[1].datestart)
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
