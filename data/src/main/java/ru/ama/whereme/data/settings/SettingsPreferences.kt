package ru.ama.whereme.data.settings

import androidx.datastore.preferences.core.*
import java.io.IOException
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.ThemeMode

internal object SettingsPreferences {
    private const val FORMAT_VERSION = 1
    private const val ALL_DAYS_MASK = (1 shl SettingsDomModel.DAYS_PER_WEEK) - 1
    val VERSION = intPreferencesKey("migration_version")
    private val DAYS = intPreferencesKey("days_mask")
    private val START = stringPreferencesKey("start")
    private val END = stringPreferencesKey("end")
    private val DISTANCE = intPreferencesKey("min_distance")
    private val ACCURACY = intPreferencesKey("accuracy")
    private val WAIT = intPreferencesKey("wait_seconds")
    private val PERIOD = intPreferencesKey("period_seconds")
    private val ENABLED = booleanPreferencesKey("schedule_enabled")
    private val THEME = stringPreferencesKey("theme")

    fun read(p: Preferences): SettingsDomModel {
        validateVersion(p)
        val d = SettingsDomModel.DEFAULT
        val mask = p[DAYS] ?: ALL_DAYS_MASK
        require(mask in 0..ALL_DAYS_MASK)
        return SettingsDomModel(List(SettingsDomModel.DAYS_PER_WEEK) { if (mask and (1 shl it) != 0) SettingsDomModel.DAY_ENABLED else SettingsDomModel.DAY_DISABLED },
            p[START] ?: d.start, p[END] ?: d.end, p[DISTANCE] ?: d.minDist,
            p[ACCURACY] ?: d.accuracy, p[WAIT] ?: d.timeOfWaitAccuracy,
            p[PERIOD] ?: d.timeOfWorkingWM, p[ENABLED] ?: d.isEnable)
    }
    fun write(p: MutablePreferences, value: SettingsDomModel) {
        validateVersion(p)
        p[VERSION] = FORMAT_VERSION
        p[DAYS] = value.days.withIndex().sumOf { (i, day) -> if (day == SettingsDomModel.DAY_ENABLED) 1 shl i else 0 }
        p[START] = value.start
        p[END] = value.end
        p[DISTANCE] = value.minDist
        p[ACCURACY] = value.accuracy
        p[WAIT] = value.timeOfWaitAccuracy
        p[PERIOD] = value.timeOfWorkingWM
        p[ENABLED] = value.isEnable
    }
    fun theme(p: Preferences): ThemeMode {
        validateVersion(p)
        val name = p[THEME] ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == name }
            ?: throw IOException("Unsupported theme; stored data retained")
    }
    fun theme(p: MutablePreferences, value: ThemeMode) {
        validateVersion(p)
        p[VERSION] = FORMAT_VERSION
        p[THEME] = value.name
    }
    private fun validateVersion(p: Preferences) {
        if (p.asMap().isNotEmpty() && p[VERSION] != FORMAT_VERSION) {
            throw IOException("Unsupported or missing settings format; stored data retained")
        }
    }
}
