package ru.ama.whereme.data.repository

import com.google.gson.Gson
import ru.ama.whereme.data.database.SettingsDataModel
import ru.ama.whereme.data.database.SettingsUserInfoDataModel
import ru.ama.whereme.data.mapper.WmMapperSettings
import ru.ama.whereme.data.mapper.WmMapperUserInfoSettings
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Legacy persistence adapter; replaced by a migrating DataStore adapter in the next step. */
@Singleton
class PreferencesRepository @Inject constructor(
    private val mapperSetTime: WmMapperSettings,
    private val mapperUserInfoSettings: WmMapperUserInfoSettings,
    private val wmSettings: WmSettings
) : SettingsRepository, SessionRepository {
    override fun getWorkingTime() = mapperSetTime.mapDataModelToDomain(
        Gson().fromJson(
            wmSettings.worktime,
            SettingsDataModel::class.java
        )
    )

    override fun setWorkingTime(dm: SettingsDomModel) {
        wmSettings.worktime = Gson().toJson(mapperSetTime.mapDomainToDataModel(dm))
    }
    override fun getWmUserInfoSetings() = mapperUserInfoSettings.mapDataModelToDomain(
        Gson().fromJson(
            wmSettings.jwToken,
            SettingsUserInfoDataModel::
            class.java
        )
    )


    override fun setWmUserInfoSetings(dm: SettingsUserInfoDomModel) {
        wmSettings.jwToken = Gson().toJson(mapperUserInfoSettings.mapDomainToDataModel(dm))
    }
}
