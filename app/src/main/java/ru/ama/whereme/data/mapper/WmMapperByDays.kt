package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModelByDays
import ru.ama.whereme.domain.entity.LocationDbByDays
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapperByDays @Inject constructor() {

    fun mapDbModelToEntity(dbModel: LocationDbModelByDays) = LocationDbByDays(
        _id = dbModel._id,
        datestart = convertLongToTime(dbModel.datestart),
        dateend = dbModel.dateend?.let { convertLongToTime(it) }
    )

    private fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("dd.MM.yyyy")
        return format.format(date)
    }

}