package ru.ama.whereme.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.domain.entity.*

interface LocationHistoryRepository {
    fun getLocationsFromBd(): Flow<List<LocationDb>>
    fun getLocationById(mDate: String): Flow<List<LocationDb>>
    fun getGropingDays(): Flow<List<LocationDbByDays>>
}
