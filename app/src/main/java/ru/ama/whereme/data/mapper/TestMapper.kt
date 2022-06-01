package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.TestInfo
import ru.ama.whereme.domain.entity.TestQuestion
import javax.inject.Inject

class TestMapper @Inject constructor() {



    fun mapDbModelToEntity(dbModel: LocationDbModel) = LocationDb(
           datetime=dbModel.datetime,
			latitude=dbModel.latitude,
		longitude=dbModel.longitude,
		sourceId=dbModel.sourceId,
		accuracy=dbModel.accuracy,
		velocity=dbModel.velocity
    )

    fun mapEntityToDbModel(db: LocationDb) = LocationDbModel(
        datetime=db.datetime,
			latitude=db.latitude,
		longitude=db.longitude,
		sourceId=db.sourceId,
		accuracy=db.accuracy,
		velocity=db.velocity
    )

    companion object {
        const val BASE_IMAGE_URL = "https://kol.hhos.ru/test/tests/img/"
    }
}
