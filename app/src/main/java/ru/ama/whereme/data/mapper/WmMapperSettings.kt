package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.*
import javax.inject.Inject

class WmMapperSettings @Inject constructor() {



    fun mapDataModelToDomain(dataModel: SettingsDataModel) = SettingsDomModel(
           days=dataModel.days,
           start =dataModel.start,
		end=dataModel.end,
        minDist =dataModel.minDist,
        accuracy =dataModel.accuracy,
        timeOfWaitAccuracy = dataModel.timeOfWaitAccuracy,
        timeOfWorkingWM = dataModel.timeOfWorkingWM
    )

    fun mapDomainToDataModel(domainModel: SettingsDomModel) = SettingsDataModel(
        days=domainModel.days,
        start =domainModel.start,
        end =domainModel.end,
        minDist =domainModel.minDist,
        accuracy =domainModel.accuracy,
        timeOfWaitAccuracy = domainModel.timeOfWaitAccuracy,
        timeOfWorkingWM = domainModel.timeOfWorkingWM
    )

  
}