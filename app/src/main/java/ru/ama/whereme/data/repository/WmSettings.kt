package ru.ama.whereme.data.repository

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import ru.ama.whereme.data.database.SettingsDataModel
import ru.ama.whereme.data.database.SettingsUserInfoDataModel
import javax.inject.Inject

class WmSettings @Inject constructor(
    private val mSettings: SharedPreferences
) {
    val defaultTime = Gson().toJson(
        SettingsDataModel(
            listOf(
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY,
                DEFAULT_SETTINGS_DAY
            ),
            DEFAULT_SETTINGS_START_TIME,
            DEFAULT_SETTINGS_END_TIME,
            DEFAULT_SETTINGS_MIN_DIST,
            DEFAULT_SETTINGS_ACCURACY,
            DEFAULT_SETTINGS_TIME_ACCURACY,
            DEFAULT_SETTINGS_TIME_PERIODIC,
            false
        )
    )
    val defaultUserInfo = Gson().toJson(
        SettingsUserInfoDataModel(
            DEFAULT_SETTINGS_EMPTY_STRING,
            DEFAULT_SETTINGS_EMPTY_INT,
            DEFAULT_SETTINGS_EMPTY_INT,
            DEFAULT_SETTINGS_EMPTY_STRING,
            DEFAULT_SETTINGS_EMPTY_STRING,
            false
        )
    )

    var worktime: String?
        get() {
            val k: String?
            if (mSettings.contains(APP_PREFERENCES_worktime)) {
                k = mSettings.getString(
                    APP_PREFERENCES_worktime,
                    defaultTime
                )
            } else
                k = defaultTime
            return k
        }
        @SuppressLint("NewApi")
        set(k) {
            val editor = mSettings.edit()
            editor.putString(APP_PREFERENCES_worktime, k)
            if (Build.VERSION.SDK_INT > 9) {
                editor.apply()
            } else
                editor.commit()
        }
    var jwToken: String
        get() {
            val k: String
            if (mSettings.contains(APP_PREFERENCES_jwt)) {
                k = mSettings.getString(
                    APP_PREFERENCES_jwt,
                    defaultUserInfo
                ).toString()
            } else
                k = defaultUserInfo
            return k
        }
        @SuppressLint("NewApi")
        set(k) {
            val editor = mSettings.edit()
            editor.putString(APP_PREFERENCES_jwt, k)
            if (Build.VERSION.SDK_INT > 9) {
                editor.apply()
            } else
                editor.commit()
        }

    private companion object {
        const val APP_PREFERENCES_worktime = "worktime"
        const val APP_PREFERENCES_jwt = "jwt"
        const val DEFAULT_SETTINGS_DAY = "1"
        const val DEFAULT_SETTINGS_START_TIME = "09:00"
        const val DEFAULT_SETTINGS_END_TIME = "17:00"
        const val DEFAULT_SETTINGS_MIN_DIST = 20
        const val DEFAULT_SETTINGS_ACCURACY = 50
        const val DEFAULT_SETTINGS_TIME_ACCURACY = 55
        const val DEFAULT_SETTINGS_TIME_PERIODIC = 100
        const val DEFAULT_SETTINGS_EMPTY_STRING = ""
        const val DEFAULT_SETTINGS_EMPTY_INT = 0
    }
}