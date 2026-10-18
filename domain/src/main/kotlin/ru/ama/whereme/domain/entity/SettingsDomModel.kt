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
        require(days.size == 7 && days.all { it == "0" || it == "1" })
        require(TIME.matches(start) && TIME.matches(end))
        require(minDist >= 0 && accuracy > 0 && timeOfWaitAccuracy > 0 && timeOfWorkingWM > 0)
    }
    companion object {
        private val TIME = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
        val DEFAULT = SettingsDomModel(List(7) { "1" }, "09:00", "17:00", 20, 50, 55, 100, false)
    }
}
