package ru.ama.whereme.data.mapper

import ru.ama.whereme.data.database.*
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import javax.inject.Inject

class WmMapperUserInfoSettings @Inject constructor() {



    fun mapDataModelToDomain(dataModel: SettingsUserInfoDataModel) = SettingsUserInfoDomModel(
         tokenJwt=dataModel.tokenJwt,
     posId=dataModel.posId,
     famId=dataModel.famId,
     name=dataModel.name,
     url=dataModel.url,
     isActivate=dataModel.isActivate
    )

    fun mapDomainToDataModel(domainModel: SettingsUserInfoDomModel) = SettingsUserInfoDataModel(
        tokenJwt=domainModel.tokenJwt,
        posId=domainModel.posId,
        famId=domainModel.famId,
        name=domainModel.name,
        url=domainModel.url,
        isActivate=domainModel.isActivate
    )
}
