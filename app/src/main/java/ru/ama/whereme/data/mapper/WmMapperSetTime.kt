package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.LocationDbModel
import ru.ama.whereme.data.database.LocationDbModelByDays
import ru.ama.whereme.data.database.SettingsDataModel
import ru.ama.whereme.data.database.SettingsDomainModel
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.LocationDbByDays
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapperSetTime @Inject constructor() {



    fun mapDataModelToDomain(dataModel: SettingsDataModel) = SettingsDomainModel(
           days=dataModel.days,
           start =dataModel.start,
		end=dataModel.end
    )

    fun mapDomainToDataModelByDays(domainModel: SettingsDomainModel) = SettingsDataModel(
        days=domainModel.days,
        start =domainModel.start,
        end =domainModel.end
    )
  
}
