package ru.ama.whereme.domain.entity

import java.time.LocalTime


data class SettingsDomModel(
    val days: List<String>,
    val start: LocalTime,
    val end: LocalTime,
    val minDist: Int,
    val accuracy: Int,
    val timeOfWaitAccuracy: Int,
    val timeOfWorkingWM: Int,
    val isEnable: Boolean
) {
    init {
        require(days.size == DAYS_PER_WEEK && days.all { it == DAY_DISABLED || it == DAY_ENABLED })
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0)
        require(minDist >= 0 && accuracy > 0 && timeOfWaitAccuracy > 0 && timeOfWorkingWM > 0)
    }
    companion object {
        const val DAYS_PER_WEEK = 7
        const val DAY_ENABLED = "1"
        const val DAY_DISABLED = "0"
        val DEFAULT = SettingsDomModel(
            days = List(DAYS_PER_WEEK) { DAY_ENABLED },
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            minDist = 20,
            accuracy = 50,
            timeOfWaitAccuracy = 55,
            timeOfWorkingWM = 100,
            isEnable = false
        )
    }
}
