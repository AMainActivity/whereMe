package ru.ama.whereme.data.mapper

import java.time.Instant
import javax.inject.Inject
import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb

class WmMapper @Inject constructor() {
    fun mapDbModelsToEntities(rows: List<LocationDbModel>): List<LocationDb> = rows.map { row ->
        LocationDb(
            _id = row._id,
            datetime = row.datetime,
            datestart = Instant.ofEpochMilli(row.datestart),
            dateend = row.dateend?.let(Instant::ofEpochMilli),
            info = row.info,
            latitude = row.latitude,
            longitude = row.longitude,
            sourceId = row.sourceId,
            accuracy = row.accuracy,
            velocity = row.velocity,
            isWrite = row.isWrite,
        )
    }
}
