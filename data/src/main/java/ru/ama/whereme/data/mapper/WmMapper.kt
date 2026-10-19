package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapper @Inject constructor() {

    fun mapDbModelsToEntities(rows: List<LocationDbModel>): List<LocationDb> {
        // One formatter per batch; it is never shared between concurrent queries/uploads.
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
        return rows.map { mapDbModelToEntity(it, format) }
    }

    private fun mapDbModelToEntity(dbModel: LocationDbModel, format: SimpleDateFormat) = LocationDb(
        _id = dbModel._id,
        datetime = dbModel.datetime,
        datestart = format.format(Date(dbModel.datestart)),
        dateend = dbModel.dateend?.let { format.format(Date(it)) },
        info = dbModel.info,
        latitude = dbModel.latitude,
        longitude = dbModel.longitude,
        sourceId = dbModel.sourceId,
        accuracy = dbModel.accuracy,
        velocity = dbModel.velocity,
        isWrite = dbModel.isWrite
    )

}