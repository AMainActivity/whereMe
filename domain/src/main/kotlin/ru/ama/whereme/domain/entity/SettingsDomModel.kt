package ru.ama.whereme.domain.entity



data class SettingsDomModel(
    var days: List<String>,
    var start: String,
    var end: String,
    var minDist: Int,
    var accuracy: Int,
    var timeOfWaitAccuracy: Int,
    var timeOfWorkingWM: Int,
    var isEnable: Boolean
)