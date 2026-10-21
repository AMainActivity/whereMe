package ru.ama.whereme.domain.repository

import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.domain.entity.LocationDb

interface LocationHistoryRepository {
    /** Timestamp of the latest locally recorded fix, including an extended stationary interval. */
    val lastRecordedTime: Flow<Instant?>
    fun getLocationById(date: LocalDate): Flow<List<LocationDb>>
}
