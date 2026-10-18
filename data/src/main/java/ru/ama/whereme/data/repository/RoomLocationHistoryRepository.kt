package ru.ama.whereme.data.repository

import kotlinx.coroutines.flow.map
import ru.ama.whereme.data.database.LocationDao
import ru.ama.whereme.data.mapper.WmMapper
import ru.ama.whereme.data.mapper.WmMapperByDays
import ru.ama.whereme.domain.repository.LocationHistoryRepository
import javax.inject.Inject

class RoomLocationHistoryRepository @Inject constructor(
    private val locationDao: LocationDao,
    private val mapper: WmMapper,
    private val mapperByDays: WmMapperByDays
) : LocationHistoryRepository {
    override fun getLocationsFromBd() = locationDao.getLocations().map { rows ->
        rows.map(mapper::mapDbModelToEntity)
    }

    override fun getLocationById(mDate: String) = locationDao.getLocationsById(mDate).map { rows ->
        rows.map(mapper::mapDbModelToEntity)
    }

    override fun getGropingDays() = locationDao.getLocationsByDays().map { rows ->
        rows.map(mapperByDays::mapDbModelToEntity)
    }
}
