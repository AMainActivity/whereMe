package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapper @Inject constructor() {


    fun mapDbModelToEntity(dbModel: LocationDbModel) = LocationDb(
        _id = dbModel._id,
        datetime = dbModel.datetime,
        datestart = convertLongToTime(dbModel.datestart),
        dateend = dbModel.dateend?.let { convertLongToTime(it) },
        info = dbModel.info,
        latitude = dbModel.latitude,
        longitude = dbModel.longitude,
        sourceId = dbModel.sourceId,
        accuracy = dbModel.accuracy,
        velocity = dbModel.velocity,
        isWrite = dbModel.isWrite
    )

    private fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        return format.format(date)
    }

}
