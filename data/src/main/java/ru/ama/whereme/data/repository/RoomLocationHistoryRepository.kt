package ru.ama.whereme.data.repository

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineDispatcher
import ru.ama.whereme.data.di.DefaultDispatcher
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import ru.ama.whereme.domain.usecase.DayRange
import javax.inject.Inject

class RoomLocationHistoryRepository @Inject constructor(
    private val dao: LocationDao, private val mapper: WmMapper,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : LocationHistoryRepository {
    override val lastRecordedTime get() = dao.observeLastRecordedTime().distinctUntilChanged()

    override fun getLocationById(date: String) = DayRange.parse(date).let { range ->
        dao.getLocations(range.start, range.end)
            .map(mapper::mapDbModelsToEntities)
            .flowOn(defaultDispatcher)
    }
}
