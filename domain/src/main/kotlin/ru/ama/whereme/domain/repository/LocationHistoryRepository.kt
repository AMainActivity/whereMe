package ru.ama.whereme.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.ama.whereme.domain.entity.LocationDb

interface LocationHistoryRepository {
    fun getLocationById(date: String): Flow<List<LocationDb>>
}
