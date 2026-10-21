package ru.ama.whereme.domain.entity



data class SettingsDomModel(
    val days: List<String>,
    val start: String,
    val end: String,
    val minDist: Int,
    val accuracy: Int,
    val timeOfWaitAccuracy: Int,
    val timeOfWorkingWM: Int,
    val isEnable: Boolean
) {
    init {
        require(days.size == DAYS_PER_WEEK && days.all { it == DAY_DISABLED || it == DAY_ENABLED })
        require(TIME.matches(start) && TIME.matches(end))
        require(minDist >= 0 && accuracy > 0 && timeOfWaitAccuracy > 0 && timeOfWorkingWM > 0)
    }
    companion object {
        const val DAYS_PER_WEEK = 7
        const val DAY_ENABLED = "1"
        const val DAY_DISABLED = "0"
        const val TIME_FORMAT = "%02d:%02d"
        private val TIME = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
        val DEFAULT = SettingsDomModel(
            days = List(DAYS_PER_WEEK) { DAY_ENABLED },
            start = "09:00",
            end = "17:00",
            minDist = 20,
            accuracy = 50,
            timeOfWaitAccuracy = 55,
            timeOfWorkingWM = 100,
            isEnable = false
        )
    }
}
