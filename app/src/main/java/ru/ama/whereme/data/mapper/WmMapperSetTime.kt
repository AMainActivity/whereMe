package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.*
import ru.ama.whereme.domain.entity.LocationDb
import ru.ama.whereme.domain.entity.LocationDbByDays
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class WmMapperSetTime @Inject constructor() {



    fun mapDataModelToDomain(dataModel: SettingsDataModelWorkTime) = SettingsDomModelWorkTime(
           days=dataModel.days,
           start =dataModel.start,
		end=dataModel.end
    )

    fun mapDomainToDataModel(domainModel: SettingsDomModelWorkTime) = SettingsDataModelWorkTime(
        days=domainModel.days,
        start =domainModel.start,
        end =domainModel.end
    )

    fun mapDataModelOtherToDom(dataModel: SettingsDataModelOther) = SettingsDomnModelOther(
           minDist =dataModel.minDist,
           accuracy =dataModel.accuracy,
		timeOfWaitAccuracy = dataModel.timeOfWaitAccuracy,
        timeOfWorkingWM = dataModel.timeOfWorkingWM
    )

    fun mapDomOtherToDataModel(domainModel: SettingsDomnModelOther) = SettingsDataModelOther(
        minDist =domainModel.minDist,
        accuracy =domainModel.accuracy,
        timeOfWaitAccuracy = domainModel.timeOfWaitAccuracy,
        timeOfWorkingWM = domainModel.timeOfWorkingWM
    )
  
}
