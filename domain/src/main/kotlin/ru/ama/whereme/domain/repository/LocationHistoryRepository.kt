package ru.ama.whereme.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.domain.entity.LocationDb

interface LocationHistoryRepository {
    /** Timestamp of the latest locally recorded fix, including an extended stationary interval. */
    val lastRecordedTime: Flow<Long?>
    fun getLocationById(date: String): Flow<List<LocationDb>>
}
