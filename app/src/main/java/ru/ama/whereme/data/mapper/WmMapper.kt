package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapper @Inject constructor() {



    fun mapDbModelToEntity(dbModel: LocationDbModel) = LocationDb(
        _id=dbModel._id,
           datetime=dbModel.datetime,
           datestart = convertLongToTime(dbModel.datestart),
		dateend=dbModel.dateend?.let{convertLongToTime(it)},
           info=dbModel.info,
			latitude=dbModel.latitude,
		longitude=dbModel.longitude,
		sourceId=dbModel.sourceId,
		accuracy=dbModel.accuracy,
		velocity=dbModel.velocity
    )

    fun mapEntityToDbModel(db: LocationDb) = LocationDbModel(
        datetime=db.datetime,
        datestart =convertDateToLong(db.datestart),
        dateend =db.dateend?.let{convertDateToLong(it)},
		info=db.info,
			latitude=db.latitude,
		longitude=db.longitude,
		sourceId=db.sourceId,
		accuracy=db.accuracy,
		velocity=db.velocity
    )
private fun convertDateToLong(date: String): Long {
    val df = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
    return df.parse(date).time
}
    private fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        return format.format(date)
    }

}
